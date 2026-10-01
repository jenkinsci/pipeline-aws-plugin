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

import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.DirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.FailedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;

import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@For(S3UploadStep.class)
@WithJenkins
class S3UploadStepTransferManagerIntegrationTest {

	private S3TransferManager transferManager;
	private S3AsyncClient asyncClient;
	private S3Client s3Client;

	@RegisterExtension
	private static final BuildWatcherExtension buildWatcher = new BuildWatcherExtension();

	private JenkinsRule jenkinsRule;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		transferManager = mock(S3TransferManager.class);
		asyncClient = mock(S3AsyncClient.class);
		s3Client = mock(S3Client.class);
		AWSClientFactory.setFactoryDelegate(x -> x instanceof S3AsyncClientBuilder ? asyncClient : s3Client);
		AWSUtilFactory.setV2TransferManagerSupplier(() -> transferManager);
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
		AWSUtilFactory.setV2TransferManagerSupplier(null);
	}

	@Test
	void useFileListUploaderWhenIncludePathPatternDefined() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadStepTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'work/subdir/test.txt', text: 'Hello!'
                  s3Upload(bucket: 'test-bucket', includePathPattern: '**/*.txt', workingDir: 'work')\
                }
                """, true)
		);

		FileUpload upload = mock(FileUpload.class);
		when(upload.completionFuture()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
				CompletedFileUpload.builder().response(PutObjectResponse.builder().build()).build()));
		when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(upload);

		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		// v1 handed the whole file list to uploadFileList; v2 has no such call, so the step submits
		// one uploadFile per resolved file. The key still has to be relative to workingDir, not to
		// the workspace, or the subdirectory would be lost from the object name.
		ArgumentCaptor<UploadFileRequest> captor = ArgumentCaptor.forClass(UploadFileRequest.class);
		verify(transferManager).uploadFile(captor.capture());
		verify(transferManager).close();
		verifyNoMoreInteractions(transferManager);
		// the manager does not close a client it was handed, so the step must
		verify(asyncClient).close();

		assertEquals("test-bucket", captor.getValue().putObjectRequest().bucket());
		assertEquals("subdir/test.txt", captor.getValue().putObjectRequest().key());
		assertThat(captor.getValue().source().toString(), matchesRegex("^.*subdir.test.txt$"));
		// the key is relative to workingDir, not to the workspace: 'work/' must not appear in it
		assertThat(captor.getValue().putObjectRequest().key(), not(containsString("work")));
	}

	@Test
	void usePutObjectForSmallSingleFileUpload() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadSingleFileTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'test.txt', text: 'Hello!'
                  s3Upload(bucket: 'test-bucket', file: 'test.txt', path: 'target.txt')\
                }
                """, true)
		);

		when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
				.thenReturn(PutObjectResponse.builder().build());

		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
		verify(s3Client).close();
		verifyNoInteractions(transferManager);
		assertEquals("test-bucket", captor.getValue().bucket());
		assertEquals("target.txt", captor.getValue().key());
	}

	@Test
	void shouldNotUploadAnythingWhenPatternDoNotMatchAnyFile() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadStepTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'work/subdir/test.txt', text: 'Hello!'
                  s3Upload(bucket: 'test-bucket', includePathPattern: '**/*.no-match', workingDir: 'work')\
                }
                """, true)
		);

		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("Nothing to upload", run);

		verifyNoMoreInteractions(transferManager);
	}

	/**
	 * The directory branch, which had no coverage at all.
	 *
	 * uploadDirectory hands each file's UploadFileRequest to the transformer with the bucket and key
	 * already computed. The Consumer overload of putObjectRequest builds a *fresh* request rather than
	 * mutating that one, so applying the options through it silently discarded both and submitted
	 * every upload with a null bucket and key. Running the captured transformer over a pre-populated
	 * builder is what catches that.
	 */
	@Test
	void directoryUploadKeepsTheBucketAndKeyTheTransferManagerComputed() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadDirTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'dir/a.txt', text: 'Hello!'
                  s3Upload(bucket: 'test-bucket', file: 'dir', path: 'artifacts/', kmsId: 'my-key')\
                }
                """, true)
		);

		DirectoryUpload upload = mock(DirectoryUpload.class);
		when(upload.completionFuture()).thenReturn(CompletableFuture.completedFuture(
				CompletedDirectoryUpload.builder().failedTransfers(java.util.Collections.emptyList()).build()));
		when(transferManager.uploadDirectory(any(UploadDirectoryRequest.class))).thenReturn(upload);

		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<UploadDirectoryRequest> captor = ArgumentCaptor.forClass(UploadDirectoryRequest.class);
		verify(transferManager).uploadDirectory(captor.capture());

		// v1 normalised the prefix before joining it to each key; v2 joins with a delimiter, so a
		// trailing slash here would name every object 'artifacts//a.txt'
		assertEquals("artifacts", captor.getValue().s3Prefix().orElse(null));

		UploadFileRequest.Builder perFile = UploadFileRequest.builder()
				.source(java.nio.file.Paths.get("a.txt"))
				.putObjectRequest(PutObjectRequest.builder().bucket("test-bucket").key("artifacts/a.txt").build());
		captor.getValue().uploadFileRequestTransformer().accept(perFile);
		PutObjectRequest transformed = perFile.build().putObjectRequest();

		assertEquals("test-bucket", transformed.bucket());
		assertEquals("artifacts/a.txt", transformed.key());
		assertEquals("my-key", transformed.ssekmsKeyId());
	}

	/**
	 * The failed-transfer check the changelog advertises: v2 completes the future normally and lists
	 * per-file failures, so without it a partial upload would look like a clean one.
	 */
	@Test
	void aPartlyFailedDirectoryUploadFailsTheBuild() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "S3UploadDirFailTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile file: 'dir/a.txt', text: 'Hello!'
                  s3Upload(bucket: 'test-bucket', file: 'dir')\
                }
                """, true)
		);

		DirectoryUpload upload = mock(DirectoryUpload.class);
		when(upload.completionFuture()).thenReturn(CompletableFuture.completedFuture(
				CompletedDirectoryUpload.builder().failedTransfers(java.util.Collections.singletonList(
						FailedFileUpload.builder()
								.exception(new RuntimeException("denied"))
								.request(UploadFileRequest.builder()
										.source(java.nio.file.Paths.get("a.txt"))
										.putObjectRequest(PutObjectRequest.builder().bucket("test-bucket").key("a.txt").build())
										.build())
								.build())).build()));
		when(transferManager.uploadDirectory(any(UploadDirectoryRequest.class))).thenReturn(upload);

		Run run = jenkinsRule.assertBuildStatus(hudson.model.Result.FAILURE, job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("failed for 1 file(s)", run);
	}
}
