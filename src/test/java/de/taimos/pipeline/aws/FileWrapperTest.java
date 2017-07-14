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

import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

public class FileWrapperTest {
	@Test
	public void constructorWorksAsExpected() throws Exception {
		FileWrapper file = null;

		// Test a normal file.
		file = new FileWrapper( "my-name", "my-path", false, 12, 9000 );
		Assert.assertEquals( "my-name", file.getName() );
		Assert.assertEquals( "my-path", file.getPath() );
		Assert.assertFalse( file.isDirectory() );
		Assert.assertEquals( 12, file.getLength() );
		Assert.assertEquals( 9000, file.getLastModified() );

		// Test a directory.
		// Note that if we tell it that it is a directory, then it will append
		// a trailing "/" to the path if one isn't there already.
		file = new FileWrapper( "my-name", "my-path", true, 12, 9000 );
		Assert.assertEquals( "my-name", file.getName() );
		Assert.assertEquals( "my-path/", file.getPath() );
		Assert.assertTrue( file.isDirectory() );
		Assert.assertEquals( 12, file.getLength() );
		Assert.assertEquals( 9000, file.getLastModified() );

		// Test a directory that already has a trailing "/".
		file = new FileWrapper( "my-name", "my-path/", true, 12, 9000 );
		Assert.assertEquals( "my-name", file.getName() );
		Assert.assertEquals( "my-path/", file.getPath() );
		Assert.assertTrue( file.isDirectory() );
		Assert.assertEquals( 12, file.getLength() );
		Assert.assertEquals( 9000, file.getLastModified() );
	}

	@Test
	public void pathIsUsedInAStringContext() throws Exception {
		FileWrapper file = null;

		// Test a normal file.
		file = new FileWrapper( "my-name", "my-path", false, 12, 9000 );
		Assert.assertEquals( "my-path", file.toString() );

		// Test a directory.
		// Note that if we tell it that it is a directory, then it will append
		// a trailing "/" to the path if one isn't there already.
		file = new FileWrapper( "my-name", "my-path", true, 12, 9000 );
		Assert.assertEquals( "my-path/", file.toString() );

		// Test a directory that already has a trailing "/".
		file = new FileWrapper( "my-name", "my-path/", true, 12, 9000 );
		Assert.assertEquals( "my-path/", file.toString() );
	}
}
