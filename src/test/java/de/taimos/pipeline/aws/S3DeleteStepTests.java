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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.util.List;

/**
 * Execution coverage for s3Delete's folder walk. The v1 implementation drove the listing with
 * listObjects plus listNextBatchOfObjects; v2 uses the paginator, and the mock below hands back a
 * real ListObjectsV2Iterable over the mocked client so the SDK's own paging issues the calls rather
 * than the test faking the sequence.
 */
public class S3DeleteStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	@Rule
	public Timeout timeout = Timeout.seconds(60);

	private S3Client s3Client;

	@Before
	public void setupSdk() {
		this.s3Client = Mockito.mock(S3Client.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.s3Client);
		Mockito.when(this.s3Client.listObjectsV2Paginator(Mockito.any(ListObjectsV2Request.class)))
				.thenAnswer(invocation -> new ListObjectsV2Iterable(this.s3Client, invocation.getArgument(0)));
	}

	@After
	public void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	/**
	 * The path is a folder, so the step recurses: the first listing pages twice and reports a
	 * sub-prefix, which is then listed in turn. Every key found across all of that must be deleted.
	 */
	@Test
	public void deleteFolderWalksEveryPageAndSubPrefix() throws Exception {
		Mockito.when(this.s3Client.headObject(Mockito.any(HeadObjectRequest.class)))
				.thenThrow(NoSuchKeyException.builder().statusCode(404).build());
		Mockito.when(this.s3Client.listObjectsV2(Mockito.any(ListObjectsV2Request.class)))
				.thenAnswer(invocation -> {
					ListObjectsV2Request request = invocation.getArgument(0);
					if ("top/".equals(request.prefix())) {
						if (request.continuationToken() == null) {
							return ListObjectsV2Response.builder()
									.contents(S3Object.builder().key("top/one").build())
									.commonPrefixes(CommonPrefix.builder().prefix("top/nested/").build())
									.nextContinuationToken("page2")
									.isTruncated(true)
									.build();
						}
						return ListObjectsV2Response.builder()
								.contents(S3Object.builder().key("top/two").build())
								.build();
					}
					return ListObjectsV2Response.builder()
							.contents(S3Object.builder().key("top/nested/three").build())
							.build();
				});

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3DeleteFolder");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  s3Delete(bucket: 'my-bucket', path: 'top/')\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		Mockito.verify(this.s3Client, Mockito.times(3)).deleteObject(captor.capture());
		Assertions.assertThat(captor.getAllValues())
				.extracting(DeleteObjectRequest::bucket, DeleteObjectRequest::key)
				.containsExactlyInAnyOrder(
						Assertions.tuple("my-bucket", "top/one"),
						Assertions.tuple("my-bucket", "top/two"),
						Assertions.tuple("my-bucket", "top/nested/three")
				);
	}

	/**
	 * A path that does not end in "/" is treated as a single object, so the step must head it and
	 * delete only that key - never list.
	 */
	@Test
	public void deleteFileDeletesOnlyThatKey() throws Exception {
		Mockito.when(this.s3Client.headObject(Mockito.any(HeadObjectRequest.class)))
				.thenReturn(HeadObjectResponse.builder().build());

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3DeleteFile");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  s3Delete(bucket: 'my-bucket', path: 'top/one')\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(this.s3Client).deleteObject(DeleteObjectRequest.builder()
				.bucket("my-bucket").key("top/one").build());
		Mockito.verify(this.s3Client, Mockito.never()).listObjectsV2(Mockito.any(ListObjectsV2Request.class));
	}

	/**
	 * A missing single object is a no-op, not a failure - v1's doesObjectExist guard did the same.
	 */
	@Test
	public void deleteFileSkipsAMissingObject() throws Exception {
		Mockito.when(this.s3Client.headObject(Mockito.any(HeadObjectRequest.class)))
				.thenThrow(NoSuchKeyException.builder().statusCode(404).build());

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3DeleteMissing");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  s3Delete(bucket: 'my-bucket', path: 'top/gone')\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(this.s3Client, Mockito.never()).deleteObject(Mockito.any(DeleteObjectRequest.class));
	}
}
