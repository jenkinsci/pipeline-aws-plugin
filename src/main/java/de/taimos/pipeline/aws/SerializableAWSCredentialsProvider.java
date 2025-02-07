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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.STSSessionCredentials;

import java.io.Serial;
import java.io.Serializable;

/*
 * Serialize credentials so that they can be passed back to master
 *
 */
public class SerializableAWSCredentialsProvider implements AWSCredentialsProvider, Serializable {
	private String accessKey;
	private String secretAccessKey;
	private String sessionToken;

	SerializableAWSCredentialsProvider(AWSCredentialsProvider credentialsProvider) {
		AWSCredentials credentials = credentialsProvider.getCredentials();
		this.accessKey = credentials.getAWSAccessKeyId();
		this.secretAccessKey = credentials.getAWSSecretKey();
		// A token may be required, so check class
		if (credentials.getClass() == BasicSessionCredentials.class) {
			BasicSessionCredentials castedCredentials = (BasicSessionCredentials) credentials;
			this.sessionToken = castedCredentials.getSessionToken();
		}
		if (credentials.getClass() == STSSessionCredentials.class) {
			STSSessionCredentials castedCredentials = (STSSessionCredentials) credentials;
			this.sessionToken = castedCredentials.getSessionToken();
		}
	}

	public AWSCredentials getCredentials() {
		if (this.sessionToken != null) {
			return new BasicSessionCredentials(this.accessKey, this.secretAccessKey, this.sessionToken);
		}
		return new BasicAWSCredentials(this.accessKey, this.secretAccessKey);
	}

	public void refresh() {}

	@Serial
	private static final long serialVersionUID = 1L;
}
