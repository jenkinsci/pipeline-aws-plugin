/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package de.taimos.pipeline.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.RetryPolicy;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.io.Serializable;


public class AWSClientFactory implements Serializable {

	static final String AWS_PROFILE = "AWS_PROFILE";
	static final String AWS_DEFAULT_PROFILE = "AWS_DEFAULT_PROFILE";
	static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
	static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
	static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";
	static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";
	static final String AWS_REGION = "AWS_REGION";
	static final String AWS_ENDPOINT_URL = "AWS_ENDPOINT_URL";
	static final String AWS_SDK_SOCKET_TIMEOUT = "AWS_SDK_SOCKET_TIMEOUT";
	static final String AWS_SDK_RETRIES = "AWS_SDK_RETRIES";
	static final String AWS_PIPELINE_STEPS_FROM_NODE = "AWS_PIPELINE_STEPS_FROM_NODE";
	private static AWSClientFactoryDelegate factoryDelegate;


	private AWSClientFactory() {
		//
	}

	public static <B extends AwsSyncClientBuilder<?, T>, T> T create(B clientBuilder, StepContext context) {
		if (factoryDelegate != null) {
			return (T) factoryDelegate.create(clientBuilder);
		}
		try {
			return configureBuilder(clientBuilder, context, context.get(EnvVars.class)).build();
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static <B extends AwsSyncClientBuilder<?, T>, T> T create(B clientBuilder, StepContext context, EnvVars vars) {
		if (factoryDelegate != null) {
			return (T) factoryDelegate.create(clientBuilder);
		}
		return configureBuilder(clientBuilder, context, vars).build();
	}

	public static <B extends AwsSyncClientBuilder<?, T>, T> T create(B clientBuilder, EnvVars vars) {
		return configureBuilder(clientBuilder, null, vars).build();
	}

	public static <B extends AwsSyncClientBuilder<?, ?>> B configureBuilder(final B clientBuilder, StepContext context, final EnvVars vars) {
		if (clientBuilder == null) {
			throw new IllegalArgumentException("ClientBuilder must not be null");
		}
		if (StringUtils.isNotBlank(vars.get(AWS_ENDPOINT_URL))) {
			clientBuilder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(vars.get(AWS_ENDPOINT_URL), vars.get(AWS_REGION)));
		} else {
			clientBuilder.setRegion(AWSClientFactory.getRegion(vars).getName());
		}

		clientBuilder.setCredentials(AWSClientFactory.getCredentials(vars, context));

		clientBuilder.setClientConfiguration(AWSClientFactory.getClientConfiguration(vars));
		return clientBuilder;
	}

	private static ClientConfiguration getClientConfiguration(EnvVars vars) {
		ClientConfiguration clientConfiguration = new ClientConfiguration();

		// The default SDK max retry is 3, increasing this to be more resilient to upstream errors
		Integer retries = Integer.valueOf(vars.get(AWS_SDK_RETRIES, "10"));
		clientConfiguration.setRetryPolicy(new RetryPolicy(null, null, retries, false));

		// The default SDK socket timeout is 50000, use as deafult and allow to override via environment variable
		Integer socketTimeout = Integer.valueOf(vars.get(AWS_SDK_SOCKET_TIMEOUT, "50000"));
		clientConfiguration.setSocketTimeout(socketTimeout);

		ProxyConfiguration.configure(vars, clientConfiguration);
		return clientConfiguration;
	}

	private static AWSCredentialsProvider getCredentials(EnvVars vars, StepContext context) {
		AWSCredentialsProvider provider = handleStaticCredentials(vars);
		if (provider != null) {
			return provider;
		}

		provider = handleProfile(vars);
		if (provider != null) {
			return provider;
		}

		if (context != null) {
			if (PluginImpl.getInstance().isEnableCredentialsFromNode() || Boolean.valueOf(vars.get(AWS_PIPELINE_STEPS_FROM_NODE))) {
				try {
					return AWSClientFactory.getCredentialsFromNode(context, vars);
				} catch (Exception e) {
					throw new RuntimeException("Unable to retrieve credentials from node.");
				}
			}
		}

		return new DefaultAWSCredentialsProviderChain();
	}

	private static AWSCredentialsProvider getCredentialsFromNode(StepContext context, EnvVars envVars) throws IOException, InterruptedException {
		FilePath ws = context.get(FilePath.class);
		TaskListener listener = context.get(TaskListener.class);
		SerializableAWSCredentialsProvider serializableAWSCredentialsProvider = ws.act(new AWSCredentialsProviderCallable(listener));
		return serializableAWSCredentialsProvider;
	}

	private static AWSCredentialsProvider handleProfile(EnvVars vars) {
		String profile = vars.get(AWS_PROFILE, vars.get(AWS_DEFAULT_PROFILE));
		if (profile != null) {
			return new ProfileCredentialsProvider(profile);
		}
		return null;
	}

	private static AWSCredentialsProvider handleStaticCredentials(EnvVars vars) {
		String accessKey = vars.get(AWS_ACCESS_KEY_ID);
		String secretAccessKey = vars.get(AWS_SECRET_ACCESS_KEY);
		if (accessKey != null && secretAccessKey != null) {
			String sessionToken = vars.get(AWS_SESSION_TOKEN);
			if (sessionToken != null) {
				return new AWSStaticCredentialsProvider(new BasicSessionCredentials(accessKey, secretAccessKey, sessionToken));
			}
			return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretAccessKey));
		}
		return null;
	}

	private static Region getRegion(EnvVars vars) {
		if (vars.get(AWS_DEFAULT_REGION) != null) {
			return Region.getRegion(Regions.fromName(vars.get(AWS_DEFAULT_REGION)));
		}
		if (vars.get(AWS_REGION) != null) {
			return Region.getRegion(Regions.fromName(vars.get(AWS_REGION)));
		}
		if (System.getenv(AWS_DEFAULT_REGION) != null) {
			return Region.getRegion(Regions.fromName(System.getenv(AWS_DEFAULT_REGION)));
		}
		if (System.getenv(AWS_REGION) != null) {
			return Region.getRegion(Regions.fromName(System.getenv(AWS_REGION)));
		}
		Region currentRegion = Regions.getCurrentRegion();
		if (currentRegion != null) {
			return currentRegion;
		}
		return Region.getRegion(Regions.DEFAULT_REGION);
	}

	private static final long serialVersionUID = 1L;

	@Restricted(NoExternalUse.class)
	public static void setFactoryDelegate(AWSClientFactoryDelegate factoryDelegate) {
		AWSClientFactory.factoryDelegate = factoryDelegate;
	}
}
