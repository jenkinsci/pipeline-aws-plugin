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

import com.amazonaws.services.s3.model.CannedAccessControlList;

public class S3CopyStepTest
{
	private static final String[] metas = {"a", "b"};

	@Test
	public void gettersWorkAsExpectedForFileCase() throws Exception {
		S3CopyStep step = new S3CopyStep("my-bucket", "my-path", "other-bucket", "other-path", false, false);
		step.setKmsId("alias/foo");
		step.setMetadatas(metas);
		step.setAcl(CannedAccessControlList.PublicRead);
		step.setCacheControl("my-cachecontrol");
		step.setContentType("text/plain");
		step.setContentDisposition("attachment");
		step.setSseAlgorithm("AES256");
		Assert.assertEquals("my-bucket", step.getFromBucket());
		Assert.assertEquals("my-path", step.getFromPath());
		Assert.assertEquals("other-bucket", step.getToBucket());
		Assert.assertEquals("other-path", step.getToPath());
		Assert.assertEquals("alias/foo", step.getKmsId());
		Assert.assertArrayEquals(metas, step.getMetadatas());
		Assert.assertEquals(CannedAccessControlList.PublicRead, step.getAcl());
		Assert.assertEquals("my-cachecontrol", step.getCacheControl());
		Assert.assertEquals("text/plain", step.getContentType());
		Assert.assertEquals("AES256", step.getSseAlgorithm());
		Assert.assertEquals("attachment", step.getContentDisposition());
	}
}
