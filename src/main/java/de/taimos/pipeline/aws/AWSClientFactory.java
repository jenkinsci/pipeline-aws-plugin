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

import java.io.Serializable;
import java.io.IOException;

import org.apache.commons.lang.StringUtils;

import org.jenkinsci.plugins.workflow.steps.StepContext;


public class AWSClientFactory implements Serializable {

	static final String AWS_PROFILE = "AWS_PROFILE";
	static final String AWS_DEFAULT_PROFILE = "AWS_DEFAULT_PROFILE";
	static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
	static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
	static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";
	static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";
	static final String AWS_REGION = "AWS_REGION";
	static final String AWS_ENDPOINT_URL = "AWS_ENDPOINT_URL";

	private AWSClientFactory() {
		//
	}

	public static <B extends AwsSyncClientBuilder<?, T>, T> T create(B clientBuilder, StepContext context) {
		try {
			return configureBuilder(clientBuilder, context, context.get(EnvVars.class)).build();
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static <B extends AwsSyncClientBuilder<?, T>, T> T create(B clientBuilder, StepContext context, EnvVars vars) {
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
		//the default max retry is 3. Increasing this to be more resilient to upstream errors
		clientConfiguration.setRetryPolicy(new RetryPolicy(null, null, 10, false));
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

		if (PluginImpl.getInstance().isEnableCredentialsFromNode() && context != null) {
			try {
				return AWSClientFactory.getCredentialsFromNode(context, vars);
			} catch (Exception e) {
				throw new RuntimeException("Unable to retrieve credentials from node.");
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
}
