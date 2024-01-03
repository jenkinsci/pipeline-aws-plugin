/*
 * Copyright 2018 CloudBees, Inc.
 *
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
 */

package de.taimos.pipeline.aws;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.ObjectTaggingProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;

@For(S3UploadStep.class)
public class S3UploadStepTransferManagerIntegrationTest {

	private TransferManager transferManager;

	@ClassRule
	public static BuildWatcher buildWatcher = new BuildWatcher();

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Before
	public void setupSdk() throws Exception {
		AmazonS3 amazonS3 = Mockito.mock(AmazonS3.class);
		transferManager = Mockito.mock(TransferManager.class);
		AWSClientFactory.setFactoryDelegate((x) -> amazonS3);
		AWSUtilFactory.setTransferManagerSupplier(() -> transferManager);
	}

	@Test
	public void useFileListUploaderWhenIncludePathPatternDefined() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadStepTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  writeFile file: 'work/subdir/test.txt', text: 'Hello!'\n"
				+ "  s3Upload(bucket: 'test-bucket', includePathPattern: '**/*.txt', workingDir: 'work')"
				+ "}\n", true)
		);

		MultipleFileUpload upload = Mockito.mock(MultipleFileUpload.class);
		Mockito.when(transferManager.uploadFileList(Mockito.eq("test-bucket"), Mockito.eq(""), Mockito.any(File.class), Mockito.any(List.class), Mockito.any(ObjectMetadataProvider.class), Mockito.any(ObjectTaggingProvider.class)))
				.thenReturn(upload);
		Mockito.when(upload.getSubTransfers()).thenReturn(Collections.emptyList());

		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<File> captorDirectory = ArgumentCaptor.forClass(File.class);

		Mockito.verify(transferManager).uploadFileList(
				Mockito.eq("test-bucket"),
				Mockito.eq(""),
				captorDirectory.capture(),
				captor.capture(),
				Mockito.any(ObjectMetadataProvider.class),
				Mockito.any(ObjectTaggingProvider.class));
		Mockito.verify(upload).getSubTransfers();
		Mockito.verify(upload).waitForCompletion();
		Mockito.verify(transferManager).shutdownNow();
		Mockito.verifyNoMoreInteractions(transferManager, upload);

		Assert.assertEquals(1, captor.getValue().size());
		Assert.assertEquals("test.txt", ((File)captor.getValue().get(0)).getName());
		assertThat(((File)captor.getValue().get(0)).getPath(), matchesRegex("^.*subdir.test.txt$"));
		assertThat(captorDirectory.getValue().getPath(), endsWith("work"));
		assertThat(captorDirectory.getValue().getPath(), not(containsString("subdir")));
	}

	@Test
	public void shouldNotUploadAnythingWhenPatternDoNotMatchAnyFile() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadStepTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  writeFile file: 'work/subdir/test.txt', text: 'Hello!'\n"
				+ "  s3Upload(bucket: 'test-bucket', includePathPattern: '**/*.no-match', workingDir: 'work')"
				+ "}\n", true)
		);

		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("Nothing to upload", run);

		Mockito.verifyNoMoreInteractions(transferManager);
	}
}
