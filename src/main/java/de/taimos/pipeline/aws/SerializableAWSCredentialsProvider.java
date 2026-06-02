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

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import java.io.Serializable;

/*
 * Serialize credentials so that they can be passed back to master
 *
 */
public class SerializableAWSCredentialsProvider implements AwsCredentialsProvider, Serializable {
	private String accessKey;
	private String secretAccessKey;
	private String sessionToken;

	SerializableAWSCredentialsProvider(AwsCredentialsProvider credentialsProvider) {
		AwsCredentials credentials = credentialsProvider.resolveCredentials();
		this.accessKey = credentials.accessKeyId();
		this.secretAccessKey = credentials.secretAccessKey();
		// A token may be required, so check class
		if (credentials.getClass() == AwsSessionCredentials.class) {
			AwsSessionCredentials castedCredentials = (AwsSessionCredentials) credentials;
			this.sessionToken = castedCredentials.sessionToken();
		}
		if (credentials.getClass() == AwsSessionCredentials.class) {
			AwsSessionCredentials castedCredentials = (AwsSessionCredentials) credentials;
			this.sessionToken = castedCredentials.sessionToken();
		}
	}

	@Override
	public AwsCredentials resolveCredentials() {
		if (this.sessionToken != null) {
			return AwsSessionCredentials.builder().accessKeyId(this.accessKey)
				.secretAccessKey(this.secretAccessKey).sessionToken(this.sessionToken).build();
		}
		return AwsBasicCredentials.builder().accessKeyId(this.accessKey)
			.secretAccessKey(this.secretAccessKey).build();
	}

	public void refresh() {}

	private static final long serialVersionUID = 1L;

}
