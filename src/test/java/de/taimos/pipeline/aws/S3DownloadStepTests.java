/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2026 Taimos GmbH
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

import hudson.model.Result;
import hudson.model.Run;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.DirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FailedFileDownload;
import software.amazon.awssdk.transfer.s3.model.FileDownload;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * s3Download runs its transfer inside a MasterToSlaveFileCallable. These exercise it on the
 * controller, which is where JenkinsRule runs the workspace. There is no agent-side coverage for
 * s3Download yet; s3Upload's remoting path is covered by S3UploadStepIntegrationTest, which runs on a
 * real agent.
 */
@WithJenkins
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class S3DownloadStepTests {

	private JenkinsRule jenkinsRule;

	private S3TransferManager transferManager;
	private S3AsyncClient asyncClient;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.transferManager = mock(S3TransferManager.class);
		AWSUtilFactory.setV2TransferManagerSupplier(() -> this.transferManager);
		this.asyncClient = mock(S3AsyncClient.class);
		AWSClientFactory.setFactoryDelegate(x -> this.asyncClient);
	}

	@AfterEach
	void tearDownSdk() {
		AWSUtilFactory.setV2TransferManagerSupplier(null);
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void stubFileDownload() {
		FileDownload download = mock(FileDownload.class);
		when(download.completionFuture()).thenReturn(CompletableFuture.completedFuture(
				CompletedFileDownload.builder()
						.response(software.amazon.awssdk.services.s3.model.GetObjectResponse.builder().build()).build()));
		when(this.transferManager.downloadFile(any(DownloadFileRequest.class))).thenReturn(download);
	}

	private void stubDirectoryDownload(java.util.List<FailedFileDownload> failures) {
		DirectoryDownload download = mock(DirectoryDownload.class);
		when(download.completionFuture()).thenReturn(CompletableFuture.completedFuture(
				CompletedDirectoryDownload.builder().failedTransfers(failures).build()));
		when(this.transferManager.downloadDirectory(any(DownloadDirectoryRequest.class))).thenReturn(download);
	}

	private Run run(String jobName, String args, Result expected) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  s3Download(" + args + ")\n"
				+ "}\n", true)
		);
		return this.jenkinsRule.assertBuildStatus(expected, job.scheduleBuild2(0));
	}

	@Test
	void downloadsASingleObject() throws Exception {
		this.stubFileDownload();

		this.run("s3DownloadFile", "bucket: 'my-bucket', path: 'a/b.txt', file: 'out.txt'", Result.SUCCESS);

		ArgumentCaptor<DownloadFileRequest> captor = ArgumentCaptor.forClass(DownloadFileRequest.class);
		verify(this.transferManager).downloadFile(captor.capture());
		Assertions.assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("my-bucket");
		Assertions.assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("a/b.txt");
		Assertions.assertThat(captor.getValue().destination().toString()).endsWith("out.txt");
		verify(this.transferManager, never()).downloadDirectory(any(DownloadDirectoryRequest.class));
	}

	/**
	 * A trailing slash - or no path at all - means "directory", and the prefix has to reach the
	 * listing or the whole bucket is downloaded instead of the requested subtree.
	 */
	@Test
	void aTrailingSlashDownloadsTheDirectoryUnderThatPrefix() throws Exception {
		this.stubDirectoryDownload(Collections.emptyList());

		this.run("s3DownloadDir", "bucket: 'my-bucket', path: 'a/b/', file: 'out'", Result.SUCCESS);

		ArgumentCaptor<DownloadDirectoryRequest> captor = ArgumentCaptor.forClass(DownloadDirectoryRequest.class);
		verify(this.transferManager).downloadDirectory(captor.capture());
		Assertions.assertThat(captor.getValue().bucket()).isEqualTo("my-bucket");

		ListObjectsV2Request.Builder listing = ListObjectsV2Request.builder();
		captor.getValue().listObjectsRequestTransformer().accept(listing);
		Assertions.assertThat(listing.build().prefix()).isEqualTo("a/b/");
		// the prefix is folded into the destination so v2's key normalisation reproduces v1's layout;
		// without this, reverting that leaves the whole suite green
		Assertions.assertThat(captor.getValue().destination())
				.endsWithRaw(java.nio.file.Paths.get("out", "a", "b"));
	}

	/**
	 * The one that matters: v1 threw if any file in a directory transfer failed, while v2 completes
	 * successfully and lists the failures. Without an explicit check the build would go green on a
	 * partly-downloaded directory.
	 */
	@Test
	void aPartlyFailedDirectoryDownloadFailsTheBuild() throws Exception {
		this.stubDirectoryDownload(Collections.singletonList(
				FailedFileDownload.builder()
						.exception(new RuntimeException("access denied for a/b/secret.txt"))
						.request(DownloadFileRequest.builder()
								.getObjectRequest(get -> get.bucket("my-bucket").key("a/b/secret.txt"))
								.destination(java.nio.file.Paths.get("out", "secret.txt"))
								.build())
						.build()));

		Run run = this.run("s3DownloadPartial", "bucket: 'my-bucket', path: 'a/b/', file: 'out'", Result.FAILURE);

		this.jenkinsRule.assertLogContains("failed for 1 object(s)", run);
	}

	/**
	 * The callable closes the client it built - still in process here, see the class javadoc on agent
	 * coverage. It matters most on an agent, where a leaked netty event loop per download accumulates
	 * across builds.
	 */
	@Test
	void closesBothTheTransferManagerAndTheClientItWasGiven() throws Exception {
		this.stubFileDownload();

		this.run("s3DownloadClose", "bucket: 'my-bucket', path: 'a/b.txt', file: 'out.txt'", Result.SUCCESS);

		verify(this.transferManager).close();
		verify(this.asyncClient).close();
	}

	/**
	 * v1's TransferManager created the target's parent directories; Ensure v2's downloadFile preserves
	 * this behavior.
	 */
	@Test
	void createsTheParentDirectoriesOfTheTarget() throws Exception {
		when(this.asyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
				.thenAnswer(invocation -> {
					AsyncResponseTransformer<GetObjectResponse, ?> transformer = invocation.getArgument(1);
					CompletableFuture<?> future = transformer.prepare();
					transformer.onResponse(GetObjectResponse.builder().contentLength(4L).build());
					transformer.onStream(AsyncRequestBody.fromString("data"));
					return future;
				});
		AWSUtilFactory.setV2TransferManagerSupplier(() -> S3TransferManager.builder().s3Client(this.asyncClient).build());

		this.run("s3DownloadNested", "bucket: 'my-bucket', path: 'a/b.txt', file: 'does-not-exist/yet/out.txt'", Result.SUCCESS);

		WorkflowJob job = this.jenkinsRule.jenkins.getItemByFullName("s3DownloadNested", WorkflowJob.class);
		Assertions.assertThat(this.jenkinsRule.jenkins.getWorkspaceFor(job).child("does-not-exist/yet/out.txt").readToString())
				.isEqualTo("data");
	}
}
