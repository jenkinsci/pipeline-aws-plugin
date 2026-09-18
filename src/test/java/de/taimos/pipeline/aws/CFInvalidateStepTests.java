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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.GetInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.GetInvalidationResponse;
import software.amazon.awssdk.services.cloudfront.model.Invalidation;
import software.amazon.awssdk.services.cloudfront.waiters.CloudFrontWaiter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cfInvalidate had no test at all, and its wait path is the first waiter converted to v2.
 *
 * The waiter is driven for real over the mocked client rather than being mocked itself, so the
 * SDK's own polling and acceptor logic decides when the wait ends - what is asserted is then the
 * request the step builds and the fact that it terminates.
 */
public class CFInvalidateStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	/** The wait polls; a regression in the acceptor would hang rather than fail without this. */
	@Rule
	public Timeout globalTimeout = Timeout.seconds(120);

	private CloudFrontClient cloudFront;

	@Before
	public void setupSdk() throws Exception {
		this.cloudFront = Mockito.mock(CloudFrontClient.class);
		Mockito.when(this.cloudFront.createInvalidation(Mockito.any(CreateInvalidationRequest.class)))
				.thenReturn(CreateInvalidationResponse.builder()
						.invalidation(Invalidation.builder().id("I123").build())
						.build());
		Mockito.when(this.cloudFront.waiter())
				.thenAnswer(invocation -> CloudFrontWaiter.builder().client(this.cloudFront).build());
		AWSClientFactory.setFactoryDelegate((x) -> this.cloudFront);
	}

	@After
	public void tearDownSdk() throws Exception {
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
	public void buildsTheInvalidationBatchFromThePaths() throws Exception {
		this.run("cfInvalidateBasic", "distribution: 'D123', paths: ['/index.html', '/assets/*']");

		ArgumentCaptor<CreateInvalidationRequest> captor = ArgumentCaptor.forClass(CreateInvalidationRequest.class);
		Mockito.verify(this.cloudFront).createInvalidation(captor.capture());
		CreateInvalidationRequest request = captor.getValue();
		assertThat(request.distributionId()).isEqualTo("D123");
		assertThat(request.invalidationBatch().paths().items()).containsExactly("/index.html", "/assets/*");
		assertThat(request.invalidationBatch().paths().quantity()).isEqualTo(2);
		assertThat(request.invalidationBatch().callerReference()).isNotBlank();
	}

	@Test
	public void doesNotWaitByDefault() throws Exception {
		this.run("cfInvalidateNoWait", "distribution: 'D123', paths: ['/*']");

		Mockito.verify(this.cloudFront, Mockito.never()).getInvalidation(Mockito.any(GetInvalidationRequest.class));
	}

	@Test
	public void waitsForTheInvalidationToComplete() throws Exception {
		Mockito.when(this.cloudFront.getInvalidation(Mockito.any(GetInvalidationRequest.class)))
				.thenReturn(GetInvalidationResponse.builder()
						.invalidation(Invalidation.builder().id("I123").status("Completed").build())
						.build());

		WorkflowRun run = this.run("cfInvalidateWait", "distribution: 'D123', paths: ['/*'], waitForCompletion: true");

		ArgumentCaptor<GetInvalidationRequest> captor = ArgumentCaptor.forClass(GetInvalidationRequest.class);
		Mockito.verify(this.cloudFront, Mockito.atLeastOnce()).getInvalidation(captor.capture());
		assertThat(captor.getValue().distributionId()).isEqualTo("D123");
		assertThat(captor.getValue().id()).isEqualTo("I123");
		this.jenkinsRule.assertLogContains("Invalidation I123 completed", run);
	}
}
