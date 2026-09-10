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

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;

import java.io.Serializable;

/*
 * Serialize credentials so that they can be passed back to master
 *
 */
public class SerializableAWSCredentialsProvider implements AwsCredentialsProvider, Serializable {
	private String accessKey;
	private String secretAccessKey;
	private String sessionToken;

	/**
	 * v1 tested getClass() against BasicSessionCredentials and STSSessionCredentials separately. v2
	 * collapses both into AwsSessionCredentials, and the test is against the session-credentials
	 * interface so that a provider returning its own implementation keeps its token.
	 */
	SerializableAWSCredentialsProvider(AwsCredentialsProvider credentialsProvider) {
		AwsCredentials credentials = credentialsProvider.resolveCredentials();
		this.accessKey = credentials.accessKeyId();
		this.secretAccessKey = credentials.secretAccessKey();
		if (credentials instanceof AwsSessionCredentialsIdentity) {
			this.sessionToken = ((AwsSessionCredentialsIdentity) credentials).sessionToken();
		}
	}

	@Override
	public AwsCredentials resolveCredentials() {
		if (this.sessionToken != null) {
			return AwsSessionCredentials.create(this.accessKey, this.secretAccessKey, this.sessionToken);
		}
		return AwsBasicCredentials.create(this.accessKey, this.secretAccessKey);
	}

	private static final long serialVersionUID = 1L;
}
