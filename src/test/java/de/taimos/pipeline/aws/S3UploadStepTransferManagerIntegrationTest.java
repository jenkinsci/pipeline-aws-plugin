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

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.ObjectTaggingProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import hudson.model.Run;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.*;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.util.Collections;
import java.util.List;

@For(S3UploadStep.class)
@RunWith(PowerMockRunner.class)
@PrepareForTest({AWSClientFactory.class, TransferManagerBuilder.class})
@PowerMockIgnore("javax.crypto.*")
public class S3UploadStepTransferManagerIntegrationTest {

	private TransferManager transferManager;

	@ClassRule
	public static BuildWatcher buildWatcher = new BuildWatcher();

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Before
	public void setupSdk() throws Exception {
		PowerMockito.mockStatic(AWSClientFactory.class);
		AmazonS3 amazonS3 = PowerMockito.mock(AmazonS3.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(StepContext.class)))
				.thenReturn(amazonS3);

		PowerMockito.mockStatic(TransferManagerBuilder.class);
		transferManager = Mockito.mock(TransferManager.class);
		TransferManagerBuilder transferManagerBuilder = PowerMockito.mock(TransferManagerBuilder.class);
		PowerMockito.when(TransferManagerBuilder.standard()).thenReturn(transferManagerBuilder);
		PowerMockito.when(transferManagerBuilder.withS3Client(Mockito.any(AmazonS3.class))).thenReturn(transferManagerBuilder);
		PowerMockito.when(transferManagerBuilder.build()).thenReturn(transferManager);
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
		Assertions.assertThat(((File)captor.getValue().get(0)).getPath()).matches("^.*subdir.test.txt$");
		Assertions.assertThat(captorDirectory.getValue().getPath()).endsWith("work");
		Assertions.assertThat(captorDirectory.getValue().getPath()).doesNotContain("subdir");
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
