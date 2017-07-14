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

public class S3FindFilesStepTest {
	@Test
	public void gettersWorkAsExpected() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep( "my-bucket" );
		Assert.assertEquals( "my-bucket", step.getBucket() );
	}

	@Test
	public void defaultPathIsEmpty() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep( "my-bucket" );
		Assert.assertEquals( "", step.getPath() );
	}

	@Test
	public void pathCanBeSet() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep( "my-bucket" );
		step.setPath( "path1" );
		Assert.assertEquals( "path1", step.getPath() );
		step.setPath( "path2" );
		Assert.assertEquals( "path2", step.getPath() );
	}

	@Test
	public void defaultGlobIsEmpty() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep( "my-bucket" );
		Assert.assertEquals( "", step.getGlob() );
	}

	@Test
	public void globCanBeSet() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep( "my-bucket" );
		step.setGlob( "glob1" );
		Assert.assertEquals( "glob1", step.getGlob() );
		step.setGlob( "glob2" );
		Assert.assertEquals( "glob2", step.getGlob() );
	}

	@Test
	public void defaultOnlyFilesIsFalse() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep( "my-bucket" );
		Assert.assertFalse( step.isOnlyFiles() );
	}

	@Test
	public void onlyFilesCanBeSet() throws Exception {
		S3FindFilesStep step = new S3FindFilesStep( "my-bucket" );
		step.setOnlyFiles( true );
		Assert.assertTrue( step.isOnlyFiles() );
		step.setOnlyFiles( false );
		Assert.assertFalse( step.isOnlyFiles() );
	}
}
