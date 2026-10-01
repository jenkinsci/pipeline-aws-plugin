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

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.GetInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.GetInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.Invalidation;
import software.amazon.awssdk.services.cloudfront.waiters.CloudFrontWaiter;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * cfInvalidate had no test at all, and its wait path is the first waiter converted to v2.
 *
 * The waiter is driven for real over the mocked client rather than being mocked itself, so the
 * SDK's own polling and acceptor logic decides when the wait ends - what is asserted is then the
 * request the step builds and the fact that it terminates.
 * The wait polls; a regression in the acceptor would hang rather than fail without this.
 */
@WithJenkins
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class CFInvalidateStepTests {

	private JenkinsRule jenkinsRule;

	private CloudFrontClient cloudFront;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.cloudFront = mock(CloudFrontClient.class);
		when(this.cloudFront.createInvalidation(any(CreateInvalidationRequest.class)))
				.thenReturn(CreateInvalidationResponse.builder()
						.invalidation(Invalidation.builder().id("I123").build())
						.build());
		when(this.cloudFront.waiter())
				.thenAnswer(invocation -> CloudFrontWaiter.builder().client(this.cloudFront).build());
		AWSClientFactory.setFactoryDelegate(x -> this.cloudFront);
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private WorkflowRun run(String jobName, String args) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfInvalidate(" + args + ")\n"
				+ "}\n", true)
		);
		return this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	void buildsTheInvalidationBatchFromThePaths() throws Exception {
		this.run("cfInvalidateBasic", "distribution: 'D123', paths: ['/index.html', '/assets/*']");

		ArgumentCaptor<CreateInvalidationRequest> captor = ArgumentCaptor.forClass(CreateInvalidationRequest.class);
		verify(this.cloudFront).createInvalidation(captor.capture());
		CreateInvalidationRequest request = captor.getValue();
		assertThat(request.distributionId()).isEqualTo("D123");
		assertThat(request.invalidationBatch().paths().items()).containsExactly("/index.html", "/assets/*");
		assertThat(request.invalidationBatch().paths().quantity()).isEqualTo(2);
		assertThat(request.invalidationBatch().callerReference()).isNotBlank();
	}

	@Test
	void doesNotWaitByDefault() throws Exception {
		this.run("cfInvalidateNoWait", "distribution: 'D123', paths: ['/*']");

		verify(this.cloudFront, never()).getInvalidation(any(GetInvalidationRequest.class));
	}

	@Test
	void waitsForTheInvalidationToComplete() throws Exception {
		when(this.cloudFront.getInvalidation(any(GetInvalidationRequest.class)))
				.thenReturn(GetInvalidationResponse.builder()
						.invalidation(Invalidation.builder().id("I123").status("Completed").build())
						.build());

		WorkflowRun run = this.run("cfInvalidateWait", "distribution: 'D123', paths: ['/*'], waitForCompletion: true");

		ArgumentCaptor<GetInvalidationRequest> captor = ArgumentCaptor.forClass(GetInvalidationRequest.class);
		verify(this.cloudFront, atLeastOnce()).getInvalidation(captor.capture());
		assertThat(captor.getValue().distributionId()).isEqualTo("D123");
		assertThat(captor.getValue().id()).isEqualTo("I123");
		this.jenkinsRule.assertLogContains("Invalidation I123 completed", run);
	}
}
