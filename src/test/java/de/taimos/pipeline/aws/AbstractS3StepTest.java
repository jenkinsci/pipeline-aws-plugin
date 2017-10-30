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

import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.junit.Assert;
import org.junit.Test;

public class AbstractS3StepTest {
	@Test
	public void gettersWorkAsExpected() throws Exception {
		S3DeleteStep step = new S3DeleteStep( "my-bucket", "my-path" , true, true);
		final AmazonS3ClientBuilder amazonS3ClientBuilder = step.createAmazonS3ClientBuilder();
		Assert.assertEquals( true, amazonS3ClientBuilder.isPathStyleAccessEnabled() );
		Assert.assertEquals( true, amazonS3ClientBuilder.isPayloadSigningEnabled() );
	}
}
