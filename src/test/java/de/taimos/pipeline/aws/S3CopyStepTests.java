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

import hudson.model.Run;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.Copy;
import software.amazon.awssdk.transfer.s3.model.CopyRequest;
import software.amazon.awssdk.transfer.s3.model.CompletedCopy;

import java.util.concurrent.CompletableFuture;

/**
 * s3Copy had no execution coverage at all, so the whole v1 ObjectMetadata to v2 request-field
 * translation was unpinned - and the failure mode is silent: a dropped metadataDirective means S3
 * copies the source object's metadata and ignores everything the step was asked to set.
 */
public class S3CopyStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Rule
	public Timeout timeout = Timeout.seconds(120);

	private S3TransferManager transferManager;
	private S3AsyncClient asyncClient;

	@Before
	public void setupSdk() {
		this.transferManager = Mockito.mock(S3TransferManager.class);
		Copy copy = Mockito.mock(Copy.class);
		Mockito.when(copy.completionFuture()).thenReturn(CompletableFuture.completedFuture(
				CompletedCopy.builder().response(CopyObjectResponse.builder().build()).build()));
		Mockito.when(this.transferManager.copy(Mockito.any(CopyRequest.class))).thenReturn(copy);
		AWSUtilFactory.setV2TransferManagerSupplier(() -> this.transferManager);
		// the step still builds an async client before handing it to the manager
		this.asyncClient = Mockito.mock(S3AsyncClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.asyncClient);
	}

	@After
	public void tearDownSdk() {
		AWSUtilFactory.setV2TransferManagerSupplier(null);
		AWSClientFactory.setFactoryDelegate(null);
	}

	private CopyObjectRequest runAndCapture(String jobName, String args) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  s3Copy(" + args + ")\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<CopyRequest> captor = ArgumentCaptor.forClass(CopyRequest.class);
		Mockito.verify(this.transferManager).copy(captor.capture());
		return captor.getValue().copyObjectRequest();
	}

	@Test
	public void copiesBetweenBucketsAndKeys() throws Exception {
		CopyObjectRequest request = this.runAndCapture("s3CopyPlain",
				"fromBucket: 'src', fromPath: 'a/b.txt', toBucket: 'dst', toPath: 'c/d.txt'");

		Assertions.assertThat(request.sourceBucket()).isEqualTo("src");
		Assertions.assertThat(request.sourceKey()).isEqualTo("a/b.txt");
		Assertions.assertThat(request.destinationBucket()).isEqualTo("dst");
		Assertions.assertThat(request.destinationKey()).isEqualTo("c/d.txt");
		// nothing to override, so the source object's metadata is carried across untouched
		Assertions.assertThat(request.metadataDirective()).isNull();
	}

	@Test
	public void returnsTheDestinationUrl() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3CopyReturn");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def url = s3Copy(fromBucket: 'src', fromPath: 'a', toBucket: 'dst', toPath: 'c')\n"
				+ "  echo \"url=${url}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("url=s3://dst/c", run);
	}

	/**
	 * Without METADATA_DIRECTIVE=REPLACE, S3 silently ignores every one of these and copies the
	 * source object's metadata instead - which is what v1's withNewObjectMetadata implied.
	 */
	@Test
	public void metadataOverridesRequireTheReplaceDirective() throws Exception {
		CopyObjectRequest request = this.runAndCapture("s3CopyMetadata",
				"fromBucket: 'src', fromPath: 'a', toBucket: 'dst', toPath: 'c',"
						+ " metadatas: ['k1:v1', 'k2:v2'], cacheControl: 'no-cache',"
						+ " contentType: 'text/plain', contentDisposition: 'attachment'");

		Assertions.assertThat(request.metadataDirective()).isEqualTo(MetadataDirective.REPLACE);
		Assertions.assertThat(request.metadata()).containsEntry("k1", "v1").containsEntry("k2", "v2");
		Assertions.assertThat(request.cacheControl()).isEqualTo("no-cache");
		Assertions.assertThat(request.contentType()).isEqualTo("text/plain");
		Assertions.assertThat(request.contentDisposition()).isEqualTo("attachment");
	}

	@Test
	public void aclUsesTheV1Spelling() throws Exception {
		CopyObjectRequest request = this.runAndCapture("s3CopyAcl",
				"fromBucket: 'src', fromPath: 'a', toBucket: 'dst', toPath: 'c', acl: 'PublicRead'");

		Assertions.assertThat(request.acl()).isEqualTo(ObjectCannedACL.PUBLIC_READ);
	}

	/**
	 * v1 expressed this as SSEAwsKeyManagementParams, which set both the key and the algorithm; on the
	 * v2 request they are separate fields and setting only the key would leave the object unencrypted.
	 */
	@Test
	public void kmsIdSetsBothTheKeyAndTheAlgorithm() throws Exception {
		CopyObjectRequest request = this.runAndCapture("s3CopyKms",
				"fromBucket: 'src', fromPath: 'a', toBucket: 'dst', toPath: 'c', kmsId: 'my-key'");

		Assertions.assertThat(request.ssekmsKeyId()).isEqualTo("my-key");
		Assertions.assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
	}

	@Test
	public void sseAlgorithmIsPassedThrough() throws Exception {
		CopyObjectRequest request = this.runAndCapture("s3CopySse",
				"fromBucket: 'src', fromPath: 'a', toBucket: 'dst', toPath: 'c', sseAlgorithm: 'AES256'");

		Assertions.assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
	}

	/**
	 * See S3UploadOptionsTest for the detail: an sseAlgorithm the SDK does not model went to S3 as
	 * the literal string "null". The enum accessor cannot see the difference, so this reads the
	 * string one.
	 */
	@Test
	public void anUnmodelledSseAlgorithmReachesS3VerbatimRatherThanAsNull() throws Exception {
		CopyObjectRequest request = this.runAndCapture("s3CopyUnmodelledSse",
				"fromBucket: 'src', fromPath: 'a', toBucket: 'dst', toPath: 'c', sseAlgorithm: 'AES-256'");

		Assertions.assertThat(request.serverSideEncryptionAsString()).isEqualTo("AES-256");
	}

	/**
	 * S3TransferManager closes only a client it created itself, so a client handed to it has to be
	 * closed by the caller - otherwise every copy leaks the client and its netty event loop. Nothing
	 * asserted this while the leak was present across three commits.
	 */
	@Test
	public void closesBothTheTransferManagerAndTheClientItWasGiven() throws Exception {
		this.runAndCapture("s3CopyClose", "fromBucket: 'src', fromPath: 'a', toBucket: 'dst', toPath: 'c'");

		Mockito.verify(this.transferManager).close();
		Mockito.verify(this.asyncClient).close();
	}
}
