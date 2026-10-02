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

import org.junit.jupiter.api.Test;
import de.taimos.pipeline.aws.utils.CannedAcl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class S3CopyStepTest {

	private static final String[] metas = {"a", "b"};

	@Test
	void gettersWorkAsExpectedForFileCase() {
		S3CopyStep step = new S3CopyStep("my-bucket", "my-path", "other-bucket", "other-path", false, false);
		step.setKmsId("alias/foo");
		step.setMetadatas(metas);
		step.setAcl(CannedAcl.PublicRead);
		step.setCacheControl("my-cachecontrol");
		step.setContentType("text/plain");
		step.setContentDisposition("attachment");
		step.setSseAlgorithm("AES256");
		assertEquals("my-bucket", step.getFromBucket());
		assertEquals("my-path", step.getFromPath());
		assertEquals("other-bucket", step.getToBucket());
		assertEquals("other-path", step.getToPath());
		assertEquals("alias/foo", step.getKmsId());
		assertArrayEquals(metas, step.getMetadatas());
		assertEquals(CannedAcl.PublicRead, step.getAcl());
		assertEquals("my-cachecontrol", step.getCacheControl());
		assertEquals("text/plain", step.getContentType());
		assertEquals("AES256", step.getSseAlgorithm());
		assertEquals("attachment", step.getContentDisposition());
	}
}
