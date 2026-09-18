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

package de.taimos.pipeline.aws.elb;

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetHealthResponse;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetDescription;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealth;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetHealthDescription;

/**
 * elbIsInstanceRegistered compares the target health state against the literal "healthy". v2 models
 * that state as an enum, so the comparison has to go through stateAsString(); reading the enum
 * directly would make the step always report false. Nothing else in the suite executes these steps.
 */
public class ELBHealthCheckStepsIntegrationTest {

	private static final String ARN = "arn:aws:elasticloadbalancing:us-east-1:1:targetgroup/tg/1";

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private ElasticLoadBalancingV2Client elb;

	@Before
	public void setupSdk() throws Exception {
		this.elb = Mockito.mock(ElasticLoadBalancingV2Client.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.elb);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void stubHealth(String instanceId, String state) {
		DescribeTargetHealthResponse.Builder response = DescribeTargetHealthResponse.builder();
		if (instanceId != null) {
			response.targetHealthDescriptions(TargetHealthDescription.builder()
					.target(TargetDescription.builder().id(instanceId).port(8080).build())
					.targetHealth(TargetHealth.builder().state(state).build())
					.build());
		}
		Mockito.when(this.elb.describeTargetHealth(Mockito.any(DescribeTargetHealthRequest.class)))
				.thenReturn(response.build());
	}

	private Run runStep(String jobName, String step) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def result = " + step + "(targetGroupARN: '" + ARN + "', instanceID: 'i-123', port: 8080)\n"
				+ "  echo \"result=${result}\"\n"
				+ "}\n", true)
		);
		return this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	public void registeredIsTrueForAHealthyMatchingTarget() throws Exception {
		this.stubHealth("i-123", "healthy");

		Run run = this.runStep("elbRegisteredHealthy", "elbIsInstanceRegistered");

		this.jenkinsRule.assertLogContains("result=true", run);
	}

	@Test
	public void registeredIsFalseWhileTheTargetIsStillInitial() throws Exception {
		this.stubHealth("i-123", "initial");

		Run run = this.runStep("elbRegisteredInitial", "elbIsInstanceRegistered");

		this.jenkinsRule.assertLogContains("result=false", run);
	}

	@Test
	public void registeredIsFalseForAnotherInstance() throws Exception {
		this.stubHealth("i-999", "healthy");

		Run run = this.runStep("elbRegisteredOther", "elbIsInstanceRegistered");

		this.jenkinsRule.assertLogContains("result=false", run);
	}

	@Test
	public void deregisteredIsTrueWhenTheTargetIsGone() throws Exception {
		this.stubHealth(null, null);

		Run run = this.runStep("elbDeregisteredGone", "elbIsInstanceDeregistered");

		this.jenkinsRule.assertLogContains("result=true", run);
	}

	@Test
	public void deregisteredIsFalseWhileTheTargetIsStillListed() throws Exception {
		this.stubHealth("i-123", "draining");

		Run run = this.runStep("elbDeregisteredPresent", "elbIsInstanceDeregistered");

		this.jenkinsRule.assertLogContains("result=false", run);
	}
}
