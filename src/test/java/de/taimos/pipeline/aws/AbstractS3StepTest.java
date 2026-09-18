/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2017 Taimos GmbH
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

import software.amazon.awssdk.services.s3.S3Configuration;

public class AbstractS3StepTest {
	/**
	 * v1 carried pathStyleAccessEnabled and payloadSigningEnabled on the client builder itself, where
	 * they could be read back. v2 folds both into an S3Configuration, and payloadSigningEnabled has
	 * no direct counterpart there - it is expressed as chunkedEncodingEnabled(false) - so this pins
	 * the mapping rather than the getters.
	 */
	@Test
	public void bothOptionsReachTheServiceConfiguration() throws Exception {
		S3DeleteStep step = new S3DeleteStep("my-bucket", "my-path", true, true);

		S3Configuration configuration = step.createS3ClientOptions().createS3Configuration();

		Assert.assertEquals(true, configuration.pathStyleAccessEnabled());
		Assert.assertEquals(false, configuration.chunkedEncodingEnabled());
	}

	@Test
	public void bothOptionsDefaultOff() throws Exception {
		S3DeleteStep step = new S3DeleteStep("my-bucket", "my-path", false, false);

		S3Configuration configuration = step.createS3ClientOptions().createS3Configuration();

		Assert.assertEquals(false, configuration.pathStyleAccessEnabled());
		// payloadSigningEnabled off leaves chunked encoding at the SDK default, which is on
		Assert.assertEquals(true, configuration.chunkedEncodingEnabled());
	}
}
