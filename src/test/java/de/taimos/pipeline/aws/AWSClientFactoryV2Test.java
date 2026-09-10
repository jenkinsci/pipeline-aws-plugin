/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2026 Taimos GmbH
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

import java.time.Duration;
import org.junit.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.retries.api.RetryStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the SDK v2 half of {@link AWSClientFactory}, asserting it reproduces the v1 semantics
 * these environment variables have always had.
 */
public class AWSClientFactoryV2Test {

	@Test
	public void awsDefaultRegionWinsOverAwsRegion() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_DEFAULT_REGION, "eu-central-1");
		vars.put(AWSClientFactory.AWS_REGION, "us-east-1");

		assertThat(AWSClientFactory.getV2Region(vars)).isEqualTo(Region.EU_CENTRAL_1);
	}

	@Test
	public void awsRegionIsUsedWhenDefaultRegionIsAbsent() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_REGION, "ap-southeast-2");

		assertThat(AWSClientFactory.getV2Region(vars)).isEqualTo(Region.AP_SOUTHEAST_2);
	}

	/**
	 * Regions absent from the v1 Regions enum - the reason this migration was requested - resolve
	 * fine in v2, which treats region ids as opaque strings.
	 */
	@Test
	public void resolvesRegionsUnknownToTheV1Sdk() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_REGION, "ap-southeast-5");

		assertThat(AWSClientFactory.getV2Region(vars).id()).isEqualTo("ap-southeast-5");
	}

	@Test
	public void staticCredentialsAreUsedWhenBothKeysArePresent() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_ACCESS_KEY_ID, "AKIAEXAMPLE");
		vars.put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret");

		AwsCredentialsProvider provider = AWSClientFactory.getV2Credentials(vars, null);

		assertThat(provider).isInstanceOf(StaticCredentialsProvider.class);
		AwsCredentials credentials = provider.resolveCredentials();
		assertThat(credentials.accessKeyId()).isEqualTo("AKIAEXAMPLE");
		assertThat(credentials.secretAccessKey()).isEqualTo("secret");
		assertThat(credentials).isNotInstanceOf(AwsSessionCredentials.class);
	}

	@Test
	public void aSessionTokenProducesSessionCredentials() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_ACCESS_KEY_ID, "AKIAEXAMPLE");
		vars.put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret");
		vars.put(AWSClientFactory.AWS_SESSION_TOKEN, "token");

		AwsCredentials credentials = AWSClientFactory.getV2Credentials(vars, null).resolveCredentials();

		assertThat(credentials).isInstanceOf(AwsSessionCredentials.class);
		assertThat(((AwsSessionCredentials) credentials).sessionToken()).isEqualTo("token");
	}

	/**
	 * v1 counts retries after the initial call (maxErrorRetry 10 means 11 calls), v2 counts the
	 * initial call within maxAttempts, so the value carries over as retries + 1 to keep the number
	 * of calls against AWS identical.
	 *
	 * The configuration is applied as a configurator rather than a fixed strategy - matching v1,
	 * which passed nulls for the retry condition and backoff to keep the SDK defaults and overrode
	 * only the count - so the assertion applies it to a strategy builder the way the SDK does.
	 */
	private static int resolveMaxAttempts(ClientOverrideConfiguration configuration) {
		RetryStrategy.Builder<?, ?> builder = AwsRetryStrategy.standardRetryStrategy().toBuilder();
		configuration.retryStrategyConfigurator().get().accept(builder);
		return builder.build().maxAttempts();
	}

	/**
	 * v1 hands the endpoint and AWS_REGION to EndpointConfiguration, and with no region set the SDK
	 * derives the signing region from the endpoint host. Since the plugin documents region and
	 * endpointUrl as mutually exclusive, that is the normal way withAWS(endpointUrl: ...) is used.
	 */
	@Test
	public void regionIsDerivedFromAnAwsEndpointWhenNoRegionIsSet() {
		EnvVars vars = new EnvVars();

		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://s3.eu-west-1.amazonaws.com"))
				.isEqualTo(Region.EU_WEST_1);
		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://sns.us-gov-west-1.amazonaws.com"))
				.isEqualTo(Region.US_GOV_WEST_1);
		// legacy global endpoints resolve to us-east-1 in v1
		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://sns.amazonaws.com"))
				.isEqualTo(Region.US_EAST_1);
	}

	@Test
	public void anExplicitRegionWinsOverTheEndpointHost() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_REGION, "ap-southeast-2");

		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://s3.eu-west-1.amazonaws.com"))
				.isEqualTo(Region.AP_SOUTHEAST_2);
	}

	/**
	 * A non-AWS endpoint (MinIO and friends) yields no region in v1 either; the region is arbitrary
	 * for such endpoints, so it falls through to the usual chain. Asserted as delegation rather
	 * than a literal region: the chain reads the aws.region system property, the process
	 * environment, ~/.aws/config and instance metadata, so any fixed expectation here would depend
	 * on the machine the suite runs on.
	 */
	@Test
	public void aCustomEndpointFallsBackToTheNormalChain() {
		EnvVars vars = new EnvVars();

		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://minio.mycompany.com"))
				.isEqualTo(AWSClientFactory.getV2Region(vars));
	}

	/**
	 * Endpoint forms v1's AwsHostNameUtils resolves, checked against it directly. The dash-style S3
	 * endpoint and the bare us-gov fragment are the two that a naive dotted-region regex misses,
	 * and getting them wrong means signing for the wrong region - SignatureDoesNotMatch, or a
	 * PermanentRedirect from S3.
	 */
	@Test
	public void matchesTheV1EndpointRegionParsing() {
		EnvVars vars = new EnvVars();

		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://s3-eu-west-1.amazonaws.com"))
				.isEqualTo(Region.EU_WEST_1);
		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://iam.us-gov.amazonaws.com"))
				.isEqualTo(Region.US_GOV_WEST_1);
		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://ec2.cn-north-1.amazonaws.com.cn"))
				.isEqualTo(Region.of("cn-north-1"));
		// v1 takes the segment without validating it; matched here rather than "corrected"
		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://weird.regional.amazonaws.com"))
				.isEqualTo(Region.of("regional"));
		// bucket-prefixed dash-style S3 endpoint: v1 matches the s3 fragment anywhere in the host
		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://bucket.s3-eu-west-1.amazonaws.com"))
				.isEqualTo(Region.EU_WEST_1);
		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://bucket.s3.eu-west-1.amazonaws.com"))
				.isEqualTo(Region.EU_WEST_1);
		// a host URI cannot parse (the empty domain label makes getHost() null), so it falls
		// through to the chain rather than throwing
		assertThat(AWSClientFactory.getV2RegionForEndpoint(vars, "https://svc..amazonaws.com"))
				.isEqualTo(AWSClientFactory.getV2Region(vars));
	}

	/**
	 * v2 refuses to build a client without a region even when the endpoint is overridden, so this
	 * is the case that fails outright if the region is not always set.
	 */
	@Test
	public void buildsAClientWithOnlyAnEndpointConfigured() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_ENDPOINT_URL, "https://minio.mycompany.com");
		vars.put(AWSClientFactory.AWS_ACCESS_KEY_ID, "AKIAEXAMPLE");
		vars.put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret");

		assertThat(AWSClientFactory.configureV2Builder(StsClient.builder(), null, vars).build()).isNotNull();
	}

	@Test
	public void socketTimeoutIsCarriedOver() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_SDK_SOCKET_TIMEOUT, "1234");

		assertThat(AWSClientFactory.getV2SocketTimeout(vars)).isEqualTo(Duration.ofMillis(1234));
		// v1's default socket timeout, carried over unchanged
		assertThat(AWSClientFactory.getV2SocketTimeout(new EnvVars())).isEqualTo(Duration.ofMillis(50000));
		assertThat(AWSClientFactory.getV2SyncHttpClient(vars)).isNotNull();
	}

	/**
	 * ApacheHttpClient registers its connection manager with the process-static IdleConnectionReaper
	 * and only close() deregisters it, so a client per invocation leaks a pool per step on a
	 * long-running controller. Nothing closes the synchronous clients, so they have to be shared.
	 */
	@Test
	public void syncHttpClientIsSharedPerConfiguration() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_SDK_SOCKET_TIMEOUT, "4321");

		SdkHttpClient first = AWSClientFactory.getV2SyncHttpClient(vars);
		SdkHttpClient again = AWSClientFactory.getV2SyncHttpClient(new EnvVars(vars));

		// Same instance for an equal configuration, from a separate EnvVars: the key is the resolved
		// timeout and proxy, not the identity of the map it came from.
		assertThat(again).isSameAs(first);

		EnvVars other = new EnvVars();
		other.put(AWSClientFactory.AWS_SDK_SOCKET_TIMEOUT, "8765");
		assertThat(AWSClientFactory.getV2SyncHttpClient(other)).isNotSameAs(first);
	}

	/**
	 * The mirror image of CredentialsProviderOwnershipTest, and the assumption the sharing above
	 * rests on: an SDK client must not close an SdkHttpClient it was handed. If a future SDK changed
	 * that, or a step were added that wraps a *synchronous* client in try-with-resources the way the
	 * transfer steps do for asynchronous ones, the first such step would shut the shared pool and
	 * every later synchronous step in the JVM would fail with "Connection pool shut down".
	 */
	@Test
	public void closingAServiceClientDoesNotCloseTheHttpClientItWasGiven() {
		CloseRecordingHttpClient stub = new CloseRecordingHttpClient();

		S3Client client = S3Client.builder()
				.region(Region.US_WEST_2)
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("k", "s")))
				.httpClient(stub)
				.build();
		client.close();

		assertThat(stub.closed).isFalse();
	}

	private static final class CloseRecordingHttpClient implements SdkHttpClient {
		private boolean closed;

		@Override
		public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
			throw new UnsupportedOperationException("no request is ever sent");
		}

		@Override
		public void close() {
			this.closed = true;
		}
	}

	/**
	 * The SDK default is 50 per client. That never bound while a client was built per invocation;
	 * shared, it would cap concurrent synchronous AWS requests process-wide, so an explicit and much
	 * larger value is set. Also pinned: the value takes part in the cache key, so raising it on a
	 * busy controller does not silently reuse a pool built at the old size.
	 */
	@Test
	public void maxConnectionsIsRaisedAboveTheSdkDefaultAndKeyedOn() {
		assertThat(AWSClientFactory.getV2MaxConnections(new EnvVars())).isGreaterThan(50);

		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_SDK_MAX_CONNECTIONS, "7");
		assertThat(AWSClientFactory.getV2MaxConnections(vars)).isEqualTo(7);

		EnvVars raised = new EnvVars();
		raised.put(AWSClientFactory.AWS_SDK_MAX_CONNECTIONS, "9");
		assertThat(AWSClientFactory.getV2SyncHttpClient(raised))
				.isNotSameAs(AWSClientFactory.getV2SyncHttpClient(vars));
	}

	/**
	 * The assertions above cannot see whether either setting reaches the builder: SyncHttpClientKey
	 * varies on both, so two clients built with different values are distinct instances either way,
	 * and the pool would quietly fall back to the SDK's 50 connections. This asserts the wiring
	 * itself, the way asyncSocketTimeoutCoversBothDirections does for netty.
	 */
	@Test
	public void syncClientConfigReachesTheBuilder() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_SDK_SOCKET_TIMEOUT, "1234");
		vars.put(AWSClientFactory.AWS_SDK_MAX_CONNECTIONS, "321");
		ApacheHttpClient.Builder builder = Mockito.mock(ApacheHttpClient.Builder.class, Mockito.RETURNS_SELF);

		AWSClientFactory.applySyncClientConfig(builder, vars);

		Mockito.verify(builder).socketTimeout(Duration.ofMillis(1234));
		Mockito.verify(builder).maxConnections(321);
		Mockito.verify(builder).proxyConfiguration(Mockito.any(ProxyConfiguration.class));
	}

	/** The default the shared pool actually gets, not just what the resolver returns. */
	@Test
	public void syncClientDefaultsToTheRaisedConnectionLimit() {
		ApacheHttpClient.Builder builder = Mockito.mock(ApacheHttpClient.Builder.class, Mockito.RETURNS_SELF);

		AWSClientFactory.applySyncClientConfig(builder, new EnvVars());

		Mockito.verify(builder).maxConnections(500);
	}

	private static S3ClientBuilder configureS3Builder(String endpointUrl) {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_REGION, "us-west-2");
		vars.put(AWSClientFactory.AWS_ACCESS_KEY_ID, "AKIAEXAMPLE");
		vars.put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret");
		if (endpointUrl != null) {
			vars.put(AWSClientFactory.AWS_ENDPOINT_URL, endpointUrl);
		}
		// a mock of the builder interface is also an SdkSyncClientBuilder, so the sync branch is happy
		S3ClientBuilder builder = Mockito.mock(S3ClientBuilder.class, Mockito.RETURNS_SELF);
		AWSClientFactory.configureV2Builder(builder, null, vars);
		return builder;
	}

	/**
	 * SDK 2.30 turned the CRC32 request trailer on by default, which some S3-compatible stores reject.
	 * These three cases pin the whole rule, because a refactor of the instanceof/host check would
	 * otherwise pass the entire suite: the relaxation is for non-AWS hosts only, and only for S3.
	 */
	@Test
	public void checksumsAreRelaxedForANonAwsEndpoint() {
		Mockito.verify(configureS3Builder("https://minio.mycompany.com"))
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
	}

	@Test
	public void checksumsKeepTheSdkDefaultForARealAwsEndpoint() {
		// endpointUrl is also the documented way to pin an AWS regional endpoint; those keep the
		// integrity trailer.
		Mockito.verify(configureS3Builder("https://s3.eu-west-1.amazonaws.com"), Mockito.never())
				.requestChecksumCalculation(Mockito.any(RequestChecksumCalculation.class));
	}

	@Test
	public void checksumsKeepTheSdkDefaultWithNoEndpointOverride() {
		Mockito.verify(configureS3Builder(null), Mockito.never())
				.requestChecksumCalculation(Mockito.any(RequestChecksumCalculation.class));
	}

	/**
	 * httpClient(instance) leaves ownership with the caller and the SDK never closes it, which for
	 * netty means leaking an EventLoopGroup per invocation. Only httpClientBuilder hands ownership
	 * over, so closing the SDK client shuts the event loop down with it - and nothing pinned that
	 * while three successive commits shipped the leaking form.
	 */
	@Test
	public void asyncBuildersGetAnHttpClientBuilderRatherThanAnInstance() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_REGION, "us-west-2");
		vars.put(AWSClientFactory.AWS_ACCESS_KEY_ID, "AKIAEXAMPLE");
		vars.put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret");
		S3AsyncClientBuilder builder = Mockito.mock(S3AsyncClientBuilder.class, Mockito.RETURNS_SELF);

		AWSClientFactory.configureV2Builder(builder, null, vars);

		Mockito.verify(builder).httpClientBuilder(Mockito.any(SdkAsyncHttpClient.Builder.class));
		Mockito.verify(builder, Mockito.never()).httpClient(Mockito.any(SdkAsyncHttpClient.class));
	}

	/**
	 * v1 had a single socket timeout covering both directions; netty splits them, so applying it to
	 * only one would quietly halve what AWS_SDK_SOCKET_TIMEOUT covers.
	 */
	@Test
	public void asyncSocketTimeoutCoversBothDirections() {
		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_SDK_SOCKET_TIMEOUT, "1234");
		NettyNioAsyncHttpClient.Builder builder = Mockito.mock(NettyNioAsyncHttpClient.Builder.class, Mockito.RETURNS_SELF);

		AWSClientFactory.applyAsyncTimeouts(builder, vars);

		Mockito.verify(builder).readTimeout(Duration.ofMillis(1234));
		Mockito.verify(builder).writeTimeout(Duration.ofMillis(1234));
	}

	@Test
	public void retryCountKeepsTheV1NumberOfAttempts() {
		assertThat(resolveMaxAttempts(AWSClientFactory.getV2OverrideConfiguration(new EnvVars()))).isEqualTo(11);

		EnvVars vars = new EnvVars();
		vars.put(AWSClientFactory.AWS_SDK_RETRIES, "3");
		assertThat(resolveMaxAttempts(AWSClientFactory.getV2OverrideConfiguration(vars))).isEqualTo(4);
	}
}
