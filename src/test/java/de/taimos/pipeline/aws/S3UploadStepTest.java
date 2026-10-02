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

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.Test;

import de.taimos.pipeline.aws.utils.CannedAcl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class S3UploadStepTest {

	@Test
	void gettersWorkAsExpectedForFileCase() {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("my-file");
		step.setText("my content text");
		step.setKmsId("alias/foo");
		step.setAcl(CannedAcl.PublicRead);
		step.setCacheControl("my-cachecontrol");
		step.setSseAlgorithm("AES256");
		step.setRedirectLocation("/redirect");
		assertEquals("my-file", step.getFile());
		assertEquals("my content text", step.getText());
		assertEquals("my-bucket", step.getBucket());
		assertEquals(CannedAcl.PublicRead, step.getAcl());
		assertEquals("my-cachecontrol", step.getCacheControl());
		assertEquals("AES256", step.getSseAlgorithm());
		assertEquals("alias/foo", step.getKmsId());
		assertEquals("/redirect", step.getRedirectLocation());
	}

	@Test
	void gettersWorkAsExpectedForContentDisposition() {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("my-file");
		step.setContentDisposition("attachment");
		assertEquals("my-file", step.getFile());
		assertEquals("attachment", step.getContentDisposition());
	}

	@Test
	void gettersWorkAsExpectedForPatternCase() {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setIncludePathPattern("**");
		step.setExcludePathPattern("**/*.svg");
		step.setWorkingDir("dist");
		assertEquals("dist", step.getWorkingDir());
		assertEquals("**", step.getIncludePathPattern());
		assertEquals("**/*.svg", step.getExcludePathPattern());
		assertEquals("my-bucket", step.getBucket());
	}

	@Test
	void defaultPathIsEmpty() {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("my-file");
		assertEquals("", step.getPath());
	}

	@Test
	void bucketMustBeDefined() {
		S3UploadStep step = new S3UploadStep(null, false, false);
		S3UploadStep.Execution execution = new S3UploadStep.Execution(step, mock(StepContext.class));
		Throwable t = assertThrows(IllegalArgumentException.class, execution::run);
		assertEquals("Bucket must not be null or empty", t.getMessage());
	}

	@Test
	void fileOrIncludePathPatternMustBeDefined() {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		S3UploadStep.Execution execution = new S3UploadStep.Execution(step, mock(StepContext.class));
		Throwable t = assertThrows(IllegalArgumentException.class, execution::run);
		assertEquals("At least one argument of Text, File or IncludePathPattern must be included", t.getMessage());
	}

	@Test
	void doNotAcceptFileAndIncludePathPatternArgumentsFilePattern() {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("file.txt");
		step.setIncludePathPattern("*.txt");
		S3UploadStep.Execution execution = new S3UploadStep.Execution(step, mock(StepContext.class));
		Throwable t = assertThrows(IllegalArgumentException.class, execution::run);
		assertEquals("File and IncludePathPattern cannot be used together", t.getMessage());
	}

	@Test
	void doNotAcceptFileAndIncludePathPatternArgumentsTextPattern() {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setText("Just some text content.");
		step.setIncludePathPattern("*.txt");
		S3UploadStep.Execution execution = new S3UploadStep.Execution(step, mock(StepContext.class));
		Throwable t = assertThrows(IllegalArgumentException.class, execution::run);
		assertEquals("IncludePathPattern and Text cannot be used together", t.getMessage());
	}

	@Test
	void doNotAcceptFileAndIncludePathPatternArgumentsFileText() {
		S3UploadStep step = new S3UploadStep("my-bucket", false, false);
		step.setFile("file.txt");
		step.setText("Just some text content.");
		S3UploadStep.Execution execution = new S3UploadStep.Execution(step, mock(StepContext.class));
		Throwable t = assertThrows(IllegalArgumentException.class, execution::run);
		assertEquals("Text and File cannot be used together", t.getMessage());
	}

}
