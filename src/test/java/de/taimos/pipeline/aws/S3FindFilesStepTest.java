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

import java.nio.file.Paths;
import java.util.Date;

import org.junit.Assert;
import org.junit.Test;

import com.amazonaws.services.s3.model.S3ObjectSummary;

public class S3FindFilesStepTest {
	@Test
	public void gettersWorkAsExpected() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		Assert.assertEquals("my-bucket", step.getBucket());
	}

	@Test
	public void defaultPathIsEmpty() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		Assert.assertEquals("", step.getPath());
	}

	@Test
	public void pathCanBeSet() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		step.setPath("path1");
		Assert.assertEquals("path1", step.getPath());
		step.setPath("path2");
		Assert.assertEquals("path2", step.getPath());
	}

	@Test
	public void defaultGlobIsEmpty() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		Assert.assertEquals("", step.getGlob());
	}

	@Test
	public void globCanBeSet() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		step.setGlob("glob1");
		Assert.assertEquals("glob1", step.getGlob());
		step.setGlob("glob2");
		Assert.assertEquals("glob2", step.getGlob());
	}

	@Test
	public void defaultOnlyFilesIsFalse() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		Assert.assertFalse(step.isOnlyFiles());
	}

	@Test
	public void onlyFilesCanBeSet() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		step.setOnlyFiles(true);
		Assert.assertTrue(step.isOnlyFiles());
		step.setOnlyFiles(false);
		Assert.assertFalse(step.isOnlyFiles());
	}

	@Test
	public void computeMatcherString() throws Exception {
		String matcherString;
		matcherString = S3FindFilesStep.Execution.computeMatcherString("", "");
		Assert.assertEquals("glob:*", matcherString);
		matcherString = S3FindFilesStep.Execution.computeMatcherString("path", "file.*");
		Assert.assertEquals("glob:path/file.*", matcherString);
		matcherString = S3FindFilesStep.Execution.computeMatcherString("", "file.*");
		Assert.assertEquals("glob:file.*", matcherString);
		matcherString = S3FindFilesStep.Execution.computeMatcherString("path/to", "my/**/file.*");
		Assert.assertEquals("glob:path/to/my/**/file.*", matcherString);
	}

	@Test
	public void createFileWrapperFromFolder() throws Exception {
		FileWrapper file;

		file = S3FindFilesStep.Execution.createFileWrapperFromFolder(0, Paths.get("path/to/folder"));
		Assert.assertEquals("folder", file.getName());
		Assert.assertEquals("path/to/folder/", file.getPath());
		Assert.assertTrue(file.isDirectory());
		Assert.assertEquals(0, file.getLength());
		Assert.assertEquals(0, file.getLastModified());
		file = S3FindFilesStep.Execution.createFileWrapperFromFolder(0, Paths.get("path/to/folder/"));
		Assert.assertEquals("folder", file.getName());
		Assert.assertEquals("path/to/folder/", file.getPath());
		Assert.assertTrue(file.isDirectory());
		Assert.assertEquals(0, file.getLength());
		Assert.assertEquals(0, file.getLastModified());

		file = S3FindFilesStep.Execution.createFileWrapperFromFolder(1, Paths.get("path/to/folder"));
		Assert.assertEquals("folder", file.getName());
		Assert.assertEquals("to/folder/", file.getPath());
		Assert.assertTrue(file.isDirectory());
		Assert.assertEquals(0, file.getLength());
		Assert.assertEquals(0, file.getLastModified());

		file = S3FindFilesStep.Execution.createFileWrapperFromFolder(2, Paths.get("path/to/folder"));
		Assert.assertEquals("folder", file.getName());
		Assert.assertEquals("folder/", file.getPath());
		Assert.assertTrue(file.isDirectory());
		Assert.assertEquals(0, file.getLength());
		Assert.assertEquals(0, file.getLastModified());
	}

	@Test
	public void createFileWrapperFromFile() throws Exception {
		FileWrapper file;
		S3ObjectSummary s3ObjectSummary = new S3ObjectSummary();
		s3ObjectSummary.setBucketName("my-bucket");
		s3ObjectSummary.setKey("path/to/my/file.ext");
		s3ObjectSummary.setLastModified(new Date(9000));
		s3ObjectSummary.setSize(12);

		file = S3FindFilesStep.Execution.createFileWrapperFromFile(0, Paths.get(s3ObjectSummary.getKey()), s3ObjectSummary);
		Assert.assertEquals("file.ext", file.getName());
		Assert.assertEquals("path/to/my/file.ext", file.getPath());
		Assert.assertFalse(file.isDirectory());
		Assert.assertEquals(12, file.getLength());
		Assert.assertEquals(9000, file.getLastModified());

		file = S3FindFilesStep.Execution.createFileWrapperFromFile(1, Paths.get(s3ObjectSummary.getKey()), s3ObjectSummary);
		Assert.assertEquals("file.ext", file.getName());
		Assert.assertEquals("to/my/file.ext", file.getPath());
		Assert.assertFalse(file.isDirectory());
		Assert.assertEquals(12, file.getLength());
		Assert.assertEquals(9000, file.getLastModified());

		file = S3FindFilesStep.Execution.createFileWrapperFromFile(2, Paths.get(s3ObjectSummary.getKey()), s3ObjectSummary);
		Assert.assertEquals("file.ext", file.getName());
		Assert.assertEquals("my/file.ext", file.getPath());
		Assert.assertFalse(file.isDirectory());
		Assert.assertEquals(12, file.getLength());
		Assert.assertEquals(9000, file.getLastModified());
	}
}
