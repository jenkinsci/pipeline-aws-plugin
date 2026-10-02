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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileWrapperTest {

	@Test
	void constructorWorksAsExpected() {
		FileWrapper file;

		// Test a normal file.
		file = new FileWrapper("my-name", "my-path", false, 12, 9000);
		assertEquals("my-name", file.getName());
		assertEquals("my-path", file.getPath());
		assertFalse(file.isDirectory());
		assertEquals(12, file.getLength());
		assertEquals(9000, file.getLastModified());

		// Test a directory.
		// Note that if we tell it that it is a directory, then it will append
		// a trailing "/" to the path if one isn't there already.
		file = new FileWrapper("my-name", "my-path", true, 12, 9000);
		assertEquals("my-name", file.getName());
		assertEquals("my-path/", file.getPath());
		assertTrue(file.isDirectory());
		assertEquals(12, file.getLength());
		assertEquals(9000, file.getLastModified());

		// Test a directory that already has a trailing "/".
		file = new FileWrapper("my-name", "my-path/", true, 12, 9000);
		assertEquals("my-name", file.getName());
		assertEquals("my-path/", file.getPath());
		assertTrue(file.isDirectory());
		assertEquals(12, file.getLength());
		assertEquals(9000, file.getLastModified());
	}

	@Test
	void pathIsUsedInAStringContext() {
		FileWrapper file;

		// Test a normal file.
		file = new FileWrapper("my-name", "my-path", false, 12, 9000);
		assertEquals("my-path", file.toString());

		// Test a directory.
		// Note that if we tell it that it is a directory, then it will append
		// a trailing "/" to the path if one isn't there already.
		file = new FileWrapper("my-name", "my-path", true, 12, 9000);
		assertEquals("my-path/", file.toString());

		// Test a directory that already has a trailing "/".
		file = new FileWrapper("my-name", "my-path/", true, 12, 9000);
		assertEquals("my-path/", file.toString());
	}
}
