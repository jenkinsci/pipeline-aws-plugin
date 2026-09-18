/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2019 Taimos GmbH
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

import org.junit.Assert;
import org.junit.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;

/**
 * This provider carries credentials resolved on an agent back to the controller, so what it does
 * with a session token is what decides whether the controller can sign at all.
 */
public class SerializableAWSCredentialsProviderTest {

	private static final String ACCESS_KEY_ID = "access-key-id";
	private static final String SECRET_KEY_ID = "secret-key-id";
	private static final String SESSION_TOKEN = "session-token";

	@Test
	public void serializeBasicCredentials() throws Exception {
		AwsCredentialsProvider provider = StaticCredentialsProvider.create(
				AwsBasicCredentials.create(ACCESS_KEY_ID, SECRET_KEY_ID));

		AwsCredentials credentials = new SerializableAWSCredentialsProvider(provider).resolveCredentials();

		Assert.assertEquals(ACCESS_KEY_ID, credentials.accessKeyId());
		Assert.assertEquals(SECRET_KEY_ID, credentials.secretAccessKey());
		Assert.assertFalse(credentials instanceof AwsSessionCredentialsIdentity);
	}

	/**
	 * v1 tested getClass() against BasicSessionCredentials and STSSessionCredentials separately; v2
	 * collapses both into AwsSessionCredentials, and the token has to survive the round trip or the
	 * controller ends up with a key and secret that cannot sign.
	 */
	@Test
	public void serializeSessionCredentials() throws Exception {
		AwsCredentialsProvider provider = StaticCredentialsProvider.create(
				AwsSessionCredentials.create(ACCESS_KEY_ID, SECRET_KEY_ID, SESSION_TOKEN));

		AwsCredentials credentials = new SerializableAWSCredentialsProvider(provider).resolveCredentials();

		Assert.assertEquals(ACCESS_KEY_ID, credentials.accessKeyId());
		Assert.assertEquals(SECRET_KEY_ID, credentials.secretAccessKey());
		Assert.assertEquals(SESSION_TOKEN, ((AwsSessionCredentialsIdentity) credentials).sessionToken());
	}

	/**
	 * The type test is against the session-credentials interface rather than the SDK's own class, so
	 * a provider returning its own implementation keeps its token rather than silently losing it.
	 */
	@Test
	public void serializeAThirdPartySessionCredential() throws Exception {
		AwsCredentialsProvider provider = () -> new ThirdPartySessionCredentials();

		AwsCredentials credentials = new SerializableAWSCredentialsProvider(provider).resolveCredentials();

		Assert.assertEquals(SESSION_TOKEN, ((AwsSessionCredentialsIdentity) credentials).sessionToken());
	}

	private static final class ThirdPartySessionCredentials implements AwsCredentials, AwsSessionCredentialsIdentity {
		@Override
		public String accessKeyId() {
			return ACCESS_KEY_ID;
		}

		@Override
		public String secretAccessKey() {
			return SECRET_KEY_ID;
		}

		@Override
		public String sessionToken() {
			return SESSION_TOKEN;
		}
	}
}
