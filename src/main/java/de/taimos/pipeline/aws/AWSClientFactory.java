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

import com.amazonaws.regions.Regions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import software.amazon.awssdk.retries.StandardRetryStrategy;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;


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

	public static <B extends AwsClientBuilder<?, T>, T> B create(B clientBuilder, StepContext context) {
		if (factoryDelegate != null) {
			return (B) factoryDelegate.create(clientBuilder);
		}
		try {
			return configureBuilder(clientBuilder, context, context.get(EnvVars.class));
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static <B extends AwsClientBuilder<?, T>, T> B create(B clientBuilder, StepContext context, EnvVars vars) {
		if (factoryDelegate != null) {
			return (B) factoryDelegate.create(clientBuilder);
		}
		return configureBuilder(clientBuilder, context, vars);
	}

	public static <B extends AwsClientBuilder<?, T>, T> B createAsync(B clientBuilder, StepContext context, EnvVars vars) {
		if (factoryDelegate != null) {
			return (B) factoryDelegate.create(clientBuilder);
		}
		return configureBuilder(clientBuilder, context, vars);
	}

	public static <B extends AwsClientBuilder<?, T>, T> B create(B clientBuilder, EnvVars vars) {
		return configureBuilder(clientBuilder, null, vars);
	}

	public static <B extends AwsClientBuilder<?, ?>> B configureBuilder(final B clientBuilder, StepContext context, final EnvVars vars) {
		if (clientBuilder == null) {
			throw new IllegalArgumentException("ClientBuilder must not be null");
		}
		if (vars != null && StringUtils.isNotBlank(vars.get(AWS_ENDPOINT_URL))) {
			try {
				clientBuilder.endpointOverride(new URI(vars.get(AWS_ENDPOINT_URL)));
				clientBuilder.region(Region.of(vars.get(AWS_REGION)));
			} catch (Exception e) {
				throw new IllegalArgumentException(vars.get(AWS_ENDPOINT_URL));
			}
		} else {
			clientBuilder.region(AWSClientFactory.getRegion(vars));
		}

		clientBuilder.credentialsProvider(AWSClientFactory.getCredentials(vars, context));

		clientBuilder.overrideConfiguration(builder -> AWSClientFactory.getClientConfiguration(builder, vars));

		ProxyConfiguration.configure(vars, clientBuilder);

		return clientBuilder;
	}

	private static void getClientConfiguration(ClientOverrideConfiguration.Builder builder, EnvVars vars) {

		// The default SDK max retry is 3, increasing this to be more resilient to upstream errors
		int retries = Integer.parseInt(vars.get(AWS_SDK_RETRIES, "10"));
		builder.retryStrategy(StandardRetryStrategy.builder().maxAttempts(retries).build());

		// The default SDK socket timeout is 50000, use as default and allow to override via environment variable
		int socketTimeout = Integer.parseInt(vars.get(AWS_SDK_SOCKET_TIMEOUT, "50000"));
		builder.apiCallTimeout(Duration.of(socketTimeout, ChronoUnit.MILLIS));

	}

	private static AwsCredentialsProvider getCredentials(EnvVars vars, StepContext context) {
		AwsCredentialsProvider provider = handleStaticCredentials(vars);
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

		return AwsCredentialsProviderChain.builder().build();
	}

	private static AwsCredentialsProvider getCredentialsFromNode(StepContext context, EnvVars envVars) throws IOException, InterruptedException {
		FilePath ws = context.get(FilePath.class);
		TaskListener listener = context.get(TaskListener.class);
		SerializableAWSCredentialsProvider serializableAWSCredentialsProvider = ws.act(new AWSCredentialsProviderCallable(listener));
		return serializableAWSCredentialsProvider;
	}

	private static AwsCredentialsProvider handleProfile(EnvVars vars) {
		String profile = vars.get(AWS_PROFILE, vars.get(AWS_DEFAULT_PROFILE));
		if (profile != null) {
			return ProfileCredentialsProvider.create(profile);
		}
		return null;
	}

	private static AwsCredentialsProvider handleStaticCredentials(EnvVars vars) {
		String accessKey = vars.get(AWS_ACCESS_KEY_ID);
		String secretAccessKey = vars.get(AWS_SECRET_ACCESS_KEY);
		if (accessKey != null && secretAccessKey != null) {
			String sessionToken = vars.get(AWS_SESSION_TOKEN);
			if (sessionToken != null) {
				return StaticCredentialsProvider.create(AwsSessionCredentials.builder().accessKeyId(accessKey).secretAccessKey(secretAccessKey).sessionToken(sessionToken).build());
			}
			return StaticCredentialsProvider.create(AwsBasicCredentials.builder().accessKeyId(accessKey).secretAccessKey(secretAccessKey).build());
		}
		return null;
	}

	private static Region getRegion(EnvVars vars) {
		if (vars.get(AWS_DEFAULT_REGION) != null) {
			return Region.of(vars.get(AWS_DEFAULT_REGION));
		}
		if (vars.get(AWS_REGION) != null) {
			return Region.of(vars.get(AWS_REGION));
		}
		if (System.getenv(AWS_DEFAULT_REGION) != null) {
			return Region.of(System.getenv(AWS_DEFAULT_REGION));
		}
		if (System.getenv(AWS_REGION) != null) {
			return Region.of(System.getenv(AWS_REGION));
		}
		return Region.US_WEST_2; // in SDK 1, this was the default region
	}

	private static final long serialVersionUID = 1L;

	@Restricted(NoExternalUse.class)
	public static void setFactoryDelegate(AWSClientFactoryDelegate factoryDelegate) {
		AWSClientFactory.factoryDelegate = factoryDelegate;
	}
}
