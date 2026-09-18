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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.client.builder.SdkAsyncClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;


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
	static final String AWS_SDK_MAX_CONNECTIONS = "AWS_SDK_MAX_CONNECTIONS";
	static final String AWS_PIPELINE_STEPS_FROM_NODE = "AWS_PIPELINE_STEPS_FROM_NODE";
	private static AWSClientFactoryDelegate factoryDelegate;


	private AWSClientFactory() {
		//
	}

	private static final long serialVersionUID = 1L;

	@Restricted(NoExternalUse.class)
	public static void setFactoryDelegate(AWSClientFactoryDelegate factoryDelegate) {
		AWSClientFactory.factoryDelegate = factoryDelegate;
	}

	@SuppressWarnings("unchecked")
	public static <B extends AwsClientBuilder<B, C>, C> C create(B clientBuilder, StepContext context) {
		if (factoryDelegate != null) {
			return (C) factoryDelegate.create(clientBuilder);
		}
		try {
			return configureV2Builder(clientBuilder, context, context.get(EnvVars.class)).build();
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <B extends AwsClientBuilder<B, C>, C> C create(B clientBuilder, StepContext context, EnvVars vars) {
		if (factoryDelegate != null) {
			return (C) factoryDelegate.create(clientBuilder);
		}
		return configureV2Builder(clientBuilder, context, vars).build();
	}

	@SuppressWarnings("unchecked")
	public static <B extends AwsClientBuilder<B, C>, C> C create(B clientBuilder, EnvVars vars) {
		if (factoryDelegate != null) {
			return (C) factoryDelegate.create(clientBuilder);
		}
		return configureV2Builder(clientBuilder, null, vars).build();
	}

	public static <B extends AwsClientBuilder<B, C>, C> B configureV2Builder(final B clientBuilder, StepContext context, final EnvVars vars) {
		if (clientBuilder == null) {
			throw new IllegalArgumentException("ClientBuilder must not be null");
		}

		// v1 treats region and endpoint as mutually exclusive, but v2 needs a region for request
		// signing even when the endpoint is overridden, so the region is always resolved here.
		String endpointUrl = vars.get(AWS_ENDPOINT_URL);
		if (endpointUrl != null && !endpointUrl.isBlank()) {
			clientBuilder.region(getV2RegionForEndpoint(vars, endpointUrl));
			clientBuilder.endpointOverride(URI.create(endpointUrl));
			relaxChecksumsForNonAwsEndpoint(clientBuilder, endpointUrl);
		} else {
			clientBuilder.region(getV2Region(vars));
		}

		clientBuilder.credentialsProvider(getV2Credentials(vars, context));
		clientBuilder.overrideConfiguration(getV2OverrideConfiguration(vars));

		if (clientBuilder instanceof SdkSyncClientBuilder) {
			((SdkSyncClientBuilder<?, ?>) clientBuilder).httpClient(getV2SyncHttpClient(vars));
		} else if (clientBuilder instanceof SdkAsyncClientBuilder) {
			((SdkAsyncClientBuilder<?, ?>) clientBuilder).httpClientBuilder(getV2AsyncHttpClientBuilder(vars));
		} else {
			// Neither interface means the socket timeout and proxy settings resolved above would be
			// silently dropped, which is worse than refusing to build the client.
			throw new IllegalStateException("Unsupported AWS client builder: " + clientBuilder.getClass().getName());
		}

		return clientBuilder;
	}

	/**
	 * S3Presigner is not an AwsClientBuilder - it is an SdkPresigner - so it cannot go through
	 * configureV2Builder. Region, credentials and the endpoint override are resolved with the same
	 * helpers so that s3PresignURL keeps agreeing with the other S3 steps about where it is pointing
	 * and who it is.
	 *
	 * Nothing here configures an HTTP client or retries, because presigning is purely local: the
	 * signature is computed in process and no request is sent.
	 *
	 * S3Presigner.Builder has no requestChecksumCalculation, so the relaxation applied to the clients
	 * for non-AWS endpoints has no counterpart here - and needs none: a presigned PUT against SDK
	 * 2.42 signs X-Amz-SignedHeaders=host only, adding no checksum header for a store to reject.
	 */
	public static S3Presigner createS3Presigner(S3Configuration serviceConfiguration, StepContext context, EnvVars vars) {
		S3Presigner.Builder presigner = S3Presigner.builder()
				.serviceConfiguration(serviceConfiguration)
				.credentialsProvider(getV2Credentials(vars, context));

		String endpointUrl = vars.get(AWS_ENDPOINT_URL);
		if (endpointUrl != null && !endpointUrl.isBlank()) {
			presigner.region(getV2RegionForEndpoint(vars, endpointUrl));
			presigner.endpointOverride(URI.create(endpointUrl));
		} else {
			presigner.region(getV2Region(vars));
		}
		return presigner.build();
	}

	/**
	 * SDK 2.30 changed the S3 default for requestChecksumCalculation to WHEN_SUPPORTED, which adds a
	 * CRC32 trailer to uploads. Several S3-compatible stores reject that, so pointing endpointUrl at
	 * one drops back to WHEN_REQUIRED.
	 *
	 * The test is whether the endpoint is an AWS host, not merely whether an override is set:
	 * endpointUrl is also the documented way to pin a real AWS regional endpoint (see
	 * getV2RegionForEndpoint), and there is no reason to give up the integrity trailer for those.
	 * It reuses parseRegionFromEndpoint as the "is this an amazonaws.com host" test - the same parse
	 * the region resolution falls back to. Note that it is only a fallback there: an explicit
	 * AWS_REGION wins, so the signing region and this decision can legitimately read different
	 * things (region from the variable, host from the URL).
	 *
	 * This lives here rather than in AbstractS3Step because the endpoint override is only known at
	 * this point; the knob itself is on S3BaseClientBuilder, so it covers the async client too.
	 */
	private static void relaxChecksumsForNonAwsEndpoint(Object clientBuilder, String endpointUrl) {
		if (clientBuilder instanceof S3BaseClientBuilder && parseRegionFromEndpoint(endpointUrl) == null) {
			((S3BaseClientBuilder<?, ?>) clientBuilder).requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
		}
	}

	static ClientOverrideConfiguration getV2OverrideConfiguration(EnvVars vars) {
		// v1 counts retries after the initial call (maxErrorRetry = 10 means 11 total attempts),
		// while v2 maxAttempts includes it, so the value is carried over as retries + 1 to keep the
		// number of calls against AWS identical.
		int retries = Integer.parseInt(vars.get(AWS_SDK_RETRIES, "10"));
		return ClientOverrideConfiguration.builder()
				.retryStrategy(b -> b.maxAttempts(retries + 1))
				.build();
	}

	static Duration getV2SocketTimeout(EnvVars vars) {
		return Duration.ofMillis(Integer.parseInt(vars.get(AWS_SDK_SOCKET_TIMEOUT, "50000")));
	}

	/**
	 * Only S3TransferManager needs this: it has no synchronous form, so s3Upload, s3Download and
	 * s3Copy build an S3AsyncClient for it.
	 *
	 * A builder rather than a built client, so that closing the SDK client shuts the netty event loop
	 * down with it. httpClient(instance) leaves ownership with the caller and the SDK never closes it,
	 * which for netty means leaking an EventLoopGroup - threads, pooled buffers and sockets - on every
	 * invocation, on agents as well as the controller.
	 *
	 * AWS_SDK_SOCKET_TIMEOUT maps onto readTimeout and writeTimeout together. v1's ClientConfiguration
	 * had a single socket timeout covering both directions, and netty splits them, so applying it to
	 * only one would quietly halve what the setting covers.
	 */
	static software.amazon.awssdk.http.async.SdkAsyncHttpClient.Builder<?> getV2AsyncHttpClientBuilder(EnvVars vars) {
		return applyAsyncTimeouts(NettyNioAsyncHttpClient.builder(), vars)
				.proxyConfiguration(ProxyConfiguration.buildV2NettyProxyConfiguration(vars));
	}

	/**
	 * Separated out because netty's builder exposes no getters: without a seam there is no way to
	 * assert that both directions are set, and asserting only that a builder was returned pins nothing.
	 */
	static NettyNioAsyncHttpClient.Builder applyAsyncTimeouts(NettyNioAsyncHttpClient.Builder builder, EnvVars vars) {
		Duration socketTimeout = getV2SocketTimeout(vars);
		return builder
				.readTimeout(socketTimeout)
				.writeTimeout(socketTimeout);
	}

	/**
	 * Shared, and deliberately never closed.
	 *
	 * ApacheHttpClient's constructor registers its pooling connection manager with the process-static
	 * IdleConnectionReaper, and only close() deregisters it. Building one per invocation - which is
	 * what this did, and what the client builders below still ask for by handing over an instance
	 * rather than a builder - therefore leaves a connection manager, its idle sockets and a reaper
	 * entry behind on every snsPublish, cfnUpdate, s3Delete, ecrLogin or withAWS(role:) for as long
	 * as the controller runs. No synchronous client in this plugin is ever closed, so nothing
	 * releases them.
	 *
	 * Sharing one instance per distinct configuration fixes that without touching the ~45 step call
	 * sites: httpClient(instance) leaves ownership with the caller precisely so that a client can be
	 * shared this way, and closing a service client does not close it. The asynchronous path needs
	 * the opposite treatment - see getV2AsyncHttpClientBuilder - because its netty event loop must be
	 * shut down and the transfer sites do close their clients.
	 *
	 * The map is bounded by the number of distinct configurations *seen*, not by the number of
	 * invocations. Nothing evicts: a superseded entry - an admin who edits the Jenkins proxy host or
	 * rotates its password - is left in place rather than closed, because another thread may still be
	 * issuing requests on it and closing it under them would fail those with "Connection pool shut
	 * down". That trades a leak per invocation for a leak per configuration change, which is the
	 * difference between unbounded growth and a handful of entries over a controller's lifetime.
	 */
	private static final ConcurrentMap<SyncHttpClientKey, software.amazon.awssdk.http.SdkHttpClient> SYNC_HTTP_CLIENTS = new ConcurrentHashMap<>();

	/**
	 * Deliberately far above the SDK's default of 50.
	 *
	 * That default is a per-client budget, and while a client was built per invocation it never
	 * bound: each step got its own 50. Shared, it becomes a ceiling on all concurrent synchronous AWS
	 * requests in the JVM, so a wide enough parallel block would queue on connection acquisition and
	 * then fail with ConnectionPoolTimeoutException after the 10s acquire timeout - a failure the
	 * per-invocation clients could not produce. The cap is a maximum rather than a preallocation and
	 * idle connections are reaped, so a high value costs nothing on an idle controller.
	 */
	private static final String DEFAULT_MAX_CONNECTIONS = "500";

	static int getV2MaxConnections(EnvVars vars) {
		return Integer.parseInt(vars.get(AWS_SDK_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS));
	}

	static software.amazon.awssdk.http.SdkHttpClient getV2SyncHttpClient(EnvVars vars) {
		SyncHttpClientKey key = new SyncHttpClientKey(getV2SocketTimeout(vars), getV2MaxConnections(vars),
				ProxyConfiguration.v2ProxyIdentity(vars));
		return SYNC_HTTP_CLIENTS.computeIfAbsent(key, k -> applySyncClientConfig(ApacheHttpClient.builder(), vars).build());
	}

	/**
	 * Separated out for the same reason as applyAsyncTimeouts: the apache builder exposes no getters,
	 * so without a seam there is nothing to assert against. It matters more here than it looks -
	 * SyncHttpClientKey varies on the socket timeout and connection limit, so a test that only builds
	 * two clients with different settings and finds them distinct passes whether or not either
	 * setting ever reaches the builder, and the pool would silently fall back to the SDK's defaults.
	 */
	static ApacheHttpClient.Builder applySyncClientConfig(ApacheHttpClient.Builder builder, EnvVars vars) {
		return builder
				.socketTimeout(getV2SocketTimeout(vars))
				.maxConnections(getV2MaxConnections(vars))
				.proxyConfiguration(ProxyConfiguration.buildV2ProxyConfiguration(vars));
	}

	/**
	 * Everything getV2SyncHttpClient varies the client on.
	 *
	 * The proxy half is a digest from ProxyConfiguration rather than the settings themselves: the
	 * SDK's own ProxyConfiguration implements neither equals nor hashCode, so a cache keyed on it
	 * would miss every time, and keying on the resolved settings object would pin the proxy username
	 * and password in the heap for the process lifetime. A digest compares the same and retains
	 * nothing.
	 */
	private static final class SyncHttpClientKey {
		private final Duration socketTimeout;
		private final int maxConnections;
		private final String proxyDigest;

		SyncHttpClientKey(Duration socketTimeout, int maxConnections, String proxyDigest) {
			this.socketTimeout = socketTimeout;
			this.maxConnections = maxConnections;
			this.proxyDigest = proxyDigest;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof SyncHttpClientKey)) {
				return false;
			}
			SyncHttpClientKey that = (SyncHttpClientKey) other;
			return this.maxConnections == that.maxConnections
					&& Objects.equals(this.socketTimeout, that.socketTimeout)
					&& Objects.equals(this.proxyDigest, that.proxyDigest);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.socketTimeout, this.maxConnections, this.proxyDigest);
		}
	}

	static AwsCredentialsProvider getV2Credentials(EnvVars vars, StepContext context) {
		AwsCredentialsProvider provider = handleV2StaticCredentials(vars);
		if (provider != null) {
			return provider;
		}

		provider = handleV2Profile(vars);
		if (provider != null) {
			return provider;
		}

		if (context != null) {
			if (PluginImpl.getInstance().isEnableCredentialsFromNode() || Boolean.valueOf(vars.get(AWS_PIPELINE_STEPS_FROM_NODE))) {
				try {
					return AWSClientFactory.getV2CredentialsFromNode(context, vars);
				} catch (Exception e) {
					throw new RuntimeException("Unable to retrieve credentials from node.");
				}
			}
		}

		return sharedDefaultCredentials();
	}

	/**
	 * The default chain, wrapped so that closing a client cannot close it.
	 *
	 * DefaultCredentialsProvider.create() returns a shared static instance, and an SDK client or
	 * presigner does close a caller-supplied credentials provider when it is SdkAutoCloseable - both
	 * verified by CredentialsProviderOwnershipTest. Since the steps close their clients in
	 * try-with-resources, handing the instance over directly would let the first s3Upload or s3Copy
	 * of a build tear down the profile-file supplier and container/IMDS caches for the whole JVM.
	 *
	 * The wrapper delegates resolution but is not SdkAutoCloseable, so the client leaves it alone and
	 * every client keeps sharing one credential cache - which is the point of the shared instance on
	 * an EC2 agent, where the alternative is re-resolving IMDS per client.
	 */
	private static AwsCredentialsProvider sharedDefaultCredentials() {
		DefaultCredentialsProvider shared = DefaultCredentialsProvider.create();
		return shared::resolveCredentials;
	}

	private static AwsCredentialsProvider getV2CredentialsFromNode(StepContext context, EnvVars envVars) throws IOException, InterruptedException {
		FilePath ws = context.get(FilePath.class);
		TaskListener listener = context.get(TaskListener.class);
		// SerializableAWSCredentialsProvider is the v2 provider carried back from the node.
		return ws.act(new AWSCredentialsProviderCallable(listener));
	}

	private static AwsCredentialsProvider handleV2Profile(EnvVars vars) {
		String profile = vars.get(AWS_PROFILE, vars.get(AWS_DEFAULT_PROFILE));
		if (profile != null) {
			return software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider.create(profile);
		}
		return null;
	}

	private static AwsCredentialsProvider handleV2StaticCredentials(EnvVars vars) {
		String accessKey = vars.get(AWS_ACCESS_KEY_ID);
		String secretAccessKey = vars.get(AWS_SECRET_ACCESS_KEY);
		if (accessKey != null && secretAccessKey != null) {
			String sessionToken = vars.get(AWS_SESSION_TOKEN);
			if (sessionToken != null) {
				return StaticCredentialsProvider.create(AwsSessionCredentials.create(accessKey, secretAccessKey, sessionToken));
			}
			return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretAccessKey));
		}
		return null;
	}

	/**
	 * v1 passes the endpoint and {@code AWS_REGION} to {@code EndpointConfiguration}. When no region
	 * is set there, the v1 SDK derives the signing region from the endpoint host
	 * ({@code AwsHostNameUtils.parseRegion}), so {@code withAWS(endpointUrl:
	 * 'https://s3.eu-west-1.amazonaws.com')} signs for eu-west-1 without the user naming a region -
	 * and the plugin documents region and endpointUrl as mutually exclusive, so that is the normal
	 * way to use it. Resolving through the usual chain instead would sign for whatever the profile
	 * or instance metadata yields, or us-west-2, and fail in a way that is hard to trace.
	 *
	 * Hosts that are not AWS endpoints (a MinIO server, say) yield nothing here, exactly as they
	 * yield null in v1; those fall through to the normal chain, and the region is arbitrary for
	 * such endpoints anyway.
	 */
	static software.amazon.awssdk.regions.Region getV2RegionForEndpoint(EnvVars vars, String endpointUrl) {
		if (vars.get(AWS_DEFAULT_REGION) != null || vars.get(AWS_REGION) != null) {
			return getV2Region(vars);
		}
		software.amazon.awssdk.regions.Region parsed = parseRegionFromEndpoint(endpointUrl);
		if (parsed != null) {
			return parsed;
		}
		return getV2Region(vars);
	}

	// Mirrors v1's S3_ENDPOINT_PATTERN, which matches the whole fragment, so bucket-prefixed hosts
	// such as bucket.s3-eu-west-1.amazonaws.com resolve the same way as s3-eu-west-1.amazonaws.com.
	private static final Pattern S3_ENDPOINT = Pattern.compile("^(?:.+\\.)?s3[-.]([a-z0-9-]+)$");

	/**
	 * Mirrors v1's {@code AwsHostNameUtils.parseRegion} closely enough for the endpoints it
	 * resolves. Checked against v1 for: s3.eu-west-1.amazonaws.com and s3-eu-west-1.amazonaws.com
	 * (both eu-west-1), iam.us-gov.amazonaws.com (us-gov-west-1), sns.amazonaws.com (us-east-1),
	 * ec2.cn-north-1.amazonaws.com.cn (cn-north-1) and weird.regional.amazonaws.com (regional - v1
	 * simply takes the segment, it does not validate it). Hosts outside amazonaws.com yield null in
	 * v1 too, and fall through to the usual region chain here.
	 */
	private static software.amazon.awssdk.regions.Region parseRegionFromEndpoint(String endpointUrl) {
		final String host;
		try {
			host = URI.create(endpointUrl).getHost();
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (host == null) {
			return null;
		}

		String remainder;
		if (host.endsWith(".amazonaws.com.cn")) {
			remainder = host.substring(0, host.length() - ".amazonaws.com.cn".length());
		} else if (host.endsWith(".amazonaws.com")) {
			remainder = host.substring(0, host.length() - ".amazonaws.com".length());
		} else {
			return null;
		}

		// Checked before the generic split, matching v1's ordering, so that both s3-eu-west-1 and
		// bucket.s3-eu-west-1 yield eu-west-1 rather than the literal s3-eu-west-1 fragment.
		Matcher s3 = S3_ENDPOINT.matcher(remainder);
		if (s3.matches()) {
			return software.amazon.awssdk.regions.Region.of(s3.group(1));
		}

		int lastDot = remainder.lastIndexOf('.');
		if (lastDot < 0) {
			// A global endpoint such as sns.amazonaws.com, which v1 resolves to us-east-1.
			return software.amazon.awssdk.regions.Region.US_EAST_1;
		}

		String region = remainder.substring(lastDot + 1);
		if ("us-gov".equals(region)) {
			// v1 special-cases the bare us-gov fragment, as in iam.us-gov.amazonaws.com
			return software.amazon.awssdk.regions.Region.US_GOV_WEST_1;
		}
		return software.amazon.awssdk.regions.Region.of(region);
	}

	static software.amazon.awssdk.regions.Region getV2Region(EnvVars vars) {
		if (vars.get(AWS_DEFAULT_REGION) != null) {
			return software.amazon.awssdk.regions.Region.of(vars.get(AWS_DEFAULT_REGION));
		}
		if (vars.get(AWS_REGION) != null) {
			return software.amazon.awssdk.regions.Region.of(vars.get(AWS_REGION));
		}
		if (System.getenv(AWS_DEFAULT_REGION) != null) {
			return software.amazon.awssdk.regions.Region.of(System.getenv(AWS_DEFAULT_REGION));
		}
		if (System.getenv(AWS_REGION) != null) {
			return software.amazon.awssdk.regions.Region.of(System.getenv(AWS_REGION));
		}
		try {
			// v1's Regions.getCurrentRegion() returns null off-EC2; the v2 chain throws instead.
			return new DefaultAwsRegionProviderChain().getRegion();
		} catch (RuntimeException e) {
			// v1 falls back to Regions.DEFAULT_REGION, which is us-west-2.
			return software.amazon.awssdk.regions.Region.US_WEST_2;
		}
	}
}
