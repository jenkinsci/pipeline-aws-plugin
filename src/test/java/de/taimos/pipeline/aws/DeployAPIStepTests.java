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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.apigateway.model.CreateDeploymentResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class DeployAPIStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private ApiGatewayClient apiGateway;

	@Before
	public void setupSdk() throws Exception {
		this.apiGateway = Mockito.mock(ApiGatewayClient.class);
		Mockito.when(this.apiGateway.createDeployment(Mockito.any(CreateDeploymentRequest.class)))
				.thenReturn(CreateDeploymentResponse.builder().id("dep-1").build());
		AWSClientFactory.setFactoryDelegate((x) -> this.apiGateway);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private CreateDeploymentRequest runAndCapture(String jobName, String args) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  deployAPI(" + args + ")\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
		Mockito.verify(this.apiGateway).createDeployment(captor.capture());
		return captor.getValue();
	}

	@Test
	public void deploysTheApiToTheStage() throws Exception {
		CreateDeploymentRequest request = this.runAndCapture("apiDeploy", "api: 'abc123', stage: 'prod'");

		assertThat(request.restApiId()).isEqualTo("abc123");
		assertThat(request.stageName()).isEqualTo("prod");
		assertThat(request.description()).isNull();
		assertThat(request.variables()).isEmpty();
	}

	@Test
	public void passesDescriptionAndVariables() throws Exception {
		CreateDeploymentRequest request = this.runAndCapture("apiDeployVars",
				"api: 'abc123', stage: 'prod', description: 'a release', variables: ['k1=v1', 'k2=v2']");

		assertThat(request.description()).isEqualTo("a release");
		assertThat(request.variables()).containsEntry("k1", "v1").containsEntry("k2", "v2");
	}
}
