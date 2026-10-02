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
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Execution coverage for s3FindFiles' listing walk, which the migration rewrote from
 * listObjects/listNextBatchOfObjects onto the paginator. The mock hands back a real
 * ListObjectsV2Iterable so the SDK's own paging drives the calls.
 */
@WithJenkins
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class S3FindFilesStepTests {

	private JenkinsRule jenkinsRule;

	private S3Client s3Client;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.s3Client = mock(S3Client.class);
		AWSClientFactory.setFactoryDelegate(x -> this.s3Client);
		when(this.s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
				.thenAnswer(invocation -> new ListObjectsV2Iterable(this.s3Client, invocation.getArgument(0)));
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private static S3Object object(String key, long size) {
		return S3Object.builder().key(key).size(size).lastModified(Instant.ofEpochMilli(9000)).build();
	}

	/**
	 * Pins three things at once: results are collected across pages, the zero-length "key ends in /"
	 * pseudo-folders the console creates are skipped, and commonPrefixes are followed as folders.
	 */
	@Test
	void findsFilesAcrossPagesAndSkipsPseudoFolders() throws Exception {
		when(this.s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
				.thenAnswer(invocation -> {
					ListObjectsV2Request request = invocation.getArgument(0);
					if ("top/".equals(request.prefix())) {
						if (request.continuationToken() == null) {
							return ListObjectsV2Response.builder()
									.contents(object("top/one.txt", 11L), object("top/nested/", 0L))
									.commonPrefixes(CommonPrefix.builder().prefix("top/nested/").build())
									.nextContinuationToken("page2")
									.isTruncated(true)
									.build();
						}
						return ListObjectsV2Response.builder()
								.contents(object("top/two.txt", 22L))
								.build();
					}
					return ListObjectsV2Response.builder()
							.contents(object("top/nested/three.txt", 33L))
							.build();
				});

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3FindFiles");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def files = s3FindFiles(bucket: 'my-bucket', path: 'top/', glob: '**')
                  echo "count=${files.size()}"
                  echo "names=${files.collect { it.name }.sort()}"
                  echo "lengths=${files.collect { it.length }.sort()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		// the "top/nested/" object is skipped as a file, but "top/nested" still arrives as a folder
		this.jenkinsRule.assertLogContains("count=4", run);
		this.jenkinsRule.assertLogContains("names=[nested, one.txt, three.txt, two.txt]", run);
		this.jenkinsRule.assertLogContains("lengths=[0, 11, 22, 33]", run);
	}

	/**
	 * onlyFiles drops the commonPrefix entries from the result but must still recurse into them.
	 */
	@Test
	void onlyFilesExcludesFoldersButStillRecurses() throws Exception {
		when(this.s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
				.thenAnswer(invocation -> {
					ListObjectsV2Request request = invocation.getArgument(0);
					if ("top/".equals(request.prefix())) {
						return ListObjectsV2Response.builder()
								.contents(object("top/one.txt", 11L))
								.commonPrefixes(CommonPrefix.builder().prefix("top/nested/").build())
								.build();
					}
					return ListObjectsV2Response.builder()
							.contents(object("top/nested/three.txt", 33L))
							.build();
				});

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3FindFilesOnlyFiles");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def files = s3FindFiles(bucket: 'my-bucket', path: 'top/', glob: '**', onlyFiles: true)
                  echo "names=${files.collect { it.name }.sort()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("names=[one.txt, three.txt]", run);
	}
}
