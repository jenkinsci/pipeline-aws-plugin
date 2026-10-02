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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import software.amazon.awssdk.services.s3.model.S3Object;

class S3FindFilesStepTest {

	@Test
	void gettersWorkAsExpected() {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		assertEquals("my-bucket", step.getBucket());
	}

	@Test
	void defaultPathIsEmpty() {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		assertEquals("", step.getPath());
	}

	@Test
	void pathCanBeSet() {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		step.setPath("path1");
		assertEquals("path1", step.getPath());
		step.setPath("path2");
		assertEquals("path2", step.getPath());
	}

	@Test
	void defaultGlobIsEmpty() {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		assertEquals("", step.getGlob());
	}

	@Test
	void globCanBeSet() {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		step.setGlob("glob1");
		assertEquals("glob1", step.getGlob());
		step.setGlob("glob2");
		assertEquals("glob2", step.getGlob());
	}

	@Test
	void defaultOnlyFilesIsFalse() {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		assertFalse(step.isOnlyFiles());
	}

	@Test
	void onlyFilesCanBeSet() {
		S3FindFilesStep step = new S3FindFilesStep("my-bucket", false, false);
		step.setOnlyFiles(true);
		assertTrue(step.isOnlyFiles());
		step.setOnlyFiles(false);
		assertFalse(step.isOnlyFiles());
	}

	@Test
	void computeMatcherString() {
		String matcherString;
		matcherString = S3FindFilesStep.Execution.computeMatcherString("", "");
		assertEquals("glob:*", matcherString);
		matcherString = S3FindFilesStep.Execution.computeMatcherString("path", "file.*");
		assertEquals("glob:path/file.*", matcherString);
		matcherString = S3FindFilesStep.Execution.computeMatcherString("", "file.*");
		assertEquals("glob:file.*", matcherString);
		matcherString = S3FindFilesStep.Execution.computeMatcherString("path/to", "my/**/file.*");
		assertEquals("glob:path/to/my/**/file.*", matcherString);
	}

	@Test
	void createFileWrapperFromFolder() {
		FileWrapper file;

		file = S3FindFilesStep.Execution.createFileWrapperFromFolder(0, Paths.get("path/to/folder"));
		assertEquals("folder", file.getName());
		assertEquals("path/to/folder/", file.getPath());
		assertTrue(file.isDirectory());
		assertEquals(0, file.getLength());
		assertEquals(0, file.getLastModified());
		file = S3FindFilesStep.Execution.createFileWrapperFromFolder(0, Paths.get("path/to/folder/"));
		assertEquals("folder", file.getName());
		assertEquals("path/to/folder/", file.getPath());
		assertTrue(file.isDirectory());
		assertEquals(0, file.getLength());
		assertEquals(0, file.getLastModified());

		file = S3FindFilesStep.Execution.createFileWrapperFromFolder(1, Paths.get("path/to/folder"));
		assertEquals("folder", file.getName());
		assertEquals("to/folder/", file.getPath());
		assertTrue(file.isDirectory());
		assertEquals(0, file.getLength());
		assertEquals(0, file.getLastModified());

		file = S3FindFilesStep.Execution.createFileWrapperFromFolder(2, Paths.get("path/to/folder"));
		assertEquals("folder", file.getName());
		assertEquals("folder/", file.getPath());
		assertTrue(file.isDirectory());
		assertEquals(0, file.getLength());
		assertEquals(0, file.getLastModified());
	}

	@Test
	void createFileWrapperFromFile() {
		FileWrapper file;
		S3Object s3Object = S3Object.builder()
				.key("path/to/my/file.ext")
				.lastModified(new Date(9000).toInstant())
				.size(12L)
				.build();

		file = S3FindFilesStep.Execution.createFileWrapperFromFile(0, Paths.get(s3Object.key()), s3Object);
		assertEquals("file.ext", file.getName());
		assertEquals("path/to/my/file.ext", file.getPath());
		assertFalse(file.isDirectory());
		assertEquals(12, file.getLength());
		assertEquals(9000, file.getLastModified());

		file = S3FindFilesStep.Execution.createFileWrapperFromFile(1, Paths.get(s3Object.key()), s3Object);
		assertEquals("file.ext", file.getName());
		assertEquals("to/my/file.ext", file.getPath());
		assertFalse(file.isDirectory());
		assertEquals(12, file.getLength());
		assertEquals(9000, file.getLastModified());

		file = S3FindFilesStep.Execution.createFileWrapperFromFile(2, Paths.get(s3Object.key()), s3Object);
		assertEquals("file.ext", file.getName());
		assertEquals("my/file.ext", file.getPath());
		assertFalse(file.isDirectory());
		assertEquals(12, file.getLength());
		assertEquals(9000, file.getLastModified());
	}
}
