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

import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.amazonaws.services.s3.model.CannedAccessControlList;

public class S3UploadStepTest {
	@Test
	public void gettersWorkAsExpectedForFileCase() throws Exception {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("my-file");
		step.setText("my content text");
		step.setKmsId("alias/foo");
		step.setAcl(CannedAccessControlList.PublicRead);
		step.setCacheControl("my-cachecontrol");
		step.setSseAlgorithm("AES256");
		step.setRedirectLocation("/redirect");
		Assert.assertEquals("my-file", step.getFile());
		Assert.assertEquals("my content text", step.getText());
		Assert.assertEquals("my-bucket", step.getBucket());
		Assert.assertEquals(CannedAccessControlList.PublicRead, step.getAcl());
		Assert.assertEquals("my-cachecontrol", step.getCacheControl());
		Assert.assertEquals("AES256", step.getSseAlgorithm());
		Assert.assertEquals("alias/foo", step.getKmsId());
		Assert.assertEquals("/redirect", step.getRedirectLocation());
	}

	@Test
	public void gettersWorkAsExpectedForContentDisposition() throws Exception {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("my-file");
		step.setContentDisposition("attachment");
		Assert.assertEquals("my-file", step.getFile());
		Assert.assertEquals("attachment", step.getContentDisposition());
	}

	@Test
	public void gettersWorkAsExpectedForPatternCase() throws Exception {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setIncludePathPattern("**");
		step.setExcludePathPattern("**/*.svg");
		step.setWorkingDir("dist");
		Assert.assertEquals("dist", step.getWorkingDir());
		Assert.assertEquals("**", step.getIncludePathPattern());
		Assert.assertEquals("**/*.svg", step.getExcludePathPattern());
		Assert.assertEquals("my-bucket", step.getBucket());
	}

	@Test
	public void defaultPathIsEmpty() throws Exception {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("my-file");
		Assert.assertEquals("", step.getPath());
	}

	@Test
	public void bucketMustBeDefined() throws Exception {
		S3UploadStep step = new S3UploadStep(null, false, false);
		S3UploadStep.Execution execution = new S3UploadStep.Execution(step, Mockito.mock(StepContext.class));
		Throwable t = Assertions.catchThrowable(execution::run);
		Assert.assertTrue(t instanceof IllegalArgumentException);
		Assert.assertEquals("Bucket must not be null or empty", t.getMessage());
	}

	@Test
	public void fileOrIncludePathPatternMustBeDefined() throws Exception {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		S3UploadStep.Execution execution = new S3UploadStep.Execution(step, Mockito.mock(StepContext.class));
		Throwable t = Assertions.catchThrowable(execution::run);
		Assert.assertTrue(t instanceof IllegalArgumentException);
		Assert.assertEquals("File or IncludePathPattern must not be null", t.getMessage());
	}

	@Test
	public void doNotAcceptFileAndIncludePathPatternArguments() throws Exception {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("file.txt");
		step.setIncludePathPattern("*.txt");
		S3UploadStep.Execution execution = new S3UploadStep.Execution(step, Mockito.mock(StepContext.class));
		Throwable t = Assertions.catchThrowable(execution::run);
		Assert.assertTrue(t instanceof IllegalArgumentException);
		Assert.assertEquals("File and IncludePathPattern cannot be use together", t.getMessage());
	}

}
