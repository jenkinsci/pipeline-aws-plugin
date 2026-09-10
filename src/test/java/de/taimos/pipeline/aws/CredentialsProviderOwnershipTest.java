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
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.utils.SdkAutoCloseable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who owns a credentials provider, and therefore who may close it.
 *
 * The steps close their clients in try-with-resources, so this matters: if a client closes the
 * provider it was handed, and that provider is the shared default chain, the first s3Upload or
 * s3Copy of a build tears down credential resolution for the rest of the JVM.
 */
public class CredentialsProviderOwnershipTest {

	private static final class ClosableProvider implements AwsCredentialsProvider, SdkAutoCloseable {
		private boolean closed;

		@Override
		public AwsCredentials resolveCredentials() {
			return AwsBasicCredentials.create("key", "secret");
		}

		@Override
		public void close() {
			this.closed = true;
		}
	}

	/*
	 * The SDK behaviour the rest of this rests on: clients and presigners do close a caller-supplied
	 * provider. If a future SDK stops doing so, these fail and the wrapper becomes unnecessary rather
	 * than silently pointless - so they are separate methods, to report which of the three changed
	 * rather than stopping at the first.
	 */

	@Test
	public void theSyncClientClosesACallerSuppliedProvider() {
		ClosableProvider provider = new ClosableProvider();

		S3Client.builder().region(Region.US_WEST_2).credentialsProvider(provider).build().close();

		assertThat(provider.closed).isTrue();
	}

	@Test
	public void theAsyncClientClosesACallerSuppliedProvider() {
		ClosableProvider provider = new ClosableProvider();

		S3AsyncClient.builder().region(Region.US_WEST_2).credentialsProvider(provider).build().close();

		assertThat(provider.closed).isTrue();
	}

	/**
	 * Listed separately for more than symmetry: SdkPresigner's resource-ownership contract is the one
	 * that would most plausibly diverge from a service client's.
	 */
	@Test
	public void thePresignerClosesACallerSuppliedProvider() {
		ClosableProvider provider = new ClosableProvider();

		S3Presigner.builder().region(Region.US_WEST_2).credentialsProvider(provider).build().close();

		assertThat(provider.closed).isTrue();
	}

	@Test
	public void theDefaultProviderIsSharedAcrossTheJvm() {
		assertThat(DefaultCredentialsProvider.create()).isSameAs(DefaultCredentialsProvider.create());
	}

	/**
	 * Put together: what the factory hands a client must not be closeable, or closing that client
	 * would close the shared instance above.
	 */
	@Test
	public void theFactoryNeverHandsAClientTheCloseableSharedProvider() {
		// empty on purpose: the default branch is selected by the *absence* of AWS_ACCESS_KEY_ID,
		// AWS_PROFILE and AWS_PIPELINE_STEPS_FROM_NODE. Region is resolved elsewhere and is not read
		// here at all, so setting one would only suggest it mattered.
		AwsCredentialsProvider provider = AWSClientFactory.getV2Credentials(new EnvVars(), null);

		// not resolving here: with no credentials in the environment the chain throws, and what this
		// pins is ownership, not resolution
		assertThat(provider).isNotInstanceOf(SdkAutoCloseable.class);
	}
}
