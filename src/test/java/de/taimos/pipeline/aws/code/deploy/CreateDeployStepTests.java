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

package de.taimos.pipeline.aws.code.deploy;

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.codedeploy.model.CreateDeploymentResponse;
import software.amazon.awssdk.services.codedeploy.model.DeploymentGroupInfo;
import software.amazon.awssdk.services.codedeploy.model.DeploymentInfo;
import software.amazon.awssdk.services.codedeploy.model.FileExistsBehavior;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentGroupRequest;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentRequest;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentResponse;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentGroupResponse;
import software.amazon.awssdk.services.codedeploy.model.RevisionLocationType;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateDeployStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private CodeDeployClient codeDeploy;

	@Before
	public void setupSdk() throws Exception {
		this.codeDeploy = Mockito.mock(CodeDeployClient.class);
		Mockito.when(this.codeDeploy.createDeployment(Mockito.any(CreateDeploymentRequest.class)))
				.thenReturn(CreateDeploymentResponse.builder().deploymentId("d-1").build());
		Mockito.when(this.codeDeploy.getDeploymentGroup(Mockito.any(GetDeploymentGroupRequest.class)))
				.thenReturn(GetDeploymentGroupResponse.builder()
						.deploymentGroupInfo(DeploymentGroupInfo.builder().computePlatform("Server").build())
						.build());
		AWSClientFactory.setFactoryDelegate((x) -> this.codeDeploy);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private WorkflowRun run(String jobName, String args, Result expected) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  createDeployment(" + args + ")\n"
				+ "}\n", true)
		);
		return this.jenkinsRule.assertBuildStatus(expected, job.scheduleBuild2(0));
	}

	@Test
	public void buildsAnS3RevisionRequest() throws Exception {
		this.run("deployTestS3",
				"applicationName: 'app', deploymentGroupName: 'group', s3Bucket: 'b', s3Key: 'k', s3BundleType: 'zip'",
				Result.SUCCESS);

		ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
		Mockito.verify(this.codeDeploy).createDeployment(captor.capture());
		CreateDeploymentRequest request = captor.getValue();
		assertThat(request.applicationName()).isEqualTo("app");
		assertThat(request.deploymentGroupName()).isEqualTo("group");
		assertThat(request.revision().revisionType()).isEqualTo(RevisionLocationType.S3);
		assertThat(request.revision().s3Location().bucket()).isEqualTo("b");
		assertThat(request.revision().s3Location().key()).isEqualTo("k");
		assertThat(request.fileExistsBehavior()).isEqualTo(FileExistsBehavior.DISALLOW);
	}

	@Test
	public void buildsAGitHubRevisionRequest() throws Exception {
		this.run("deployTestGitHub",
				"applicationName: 'app', deploymentGroupName: 'group', gitHubRepository: 'org/repo', gitHubCommitId: 'abc123'",
				Result.SUCCESS);

		ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
		Mockito.verify(this.codeDeploy).createDeployment(captor.capture());
		assertThat(captor.getValue().revision().revisionType()).isEqualTo(RevisionLocationType.GIT_HUB);
		assertThat(captor.getValue().revision().gitHubLocation().repository()).isEqualTo("org/repo");
		assertThat(captor.getValue().revision().gitHubLocation().commitId()).isEqualTo("abc123");
	}

	/**
	 * waitForCompletion is optional, and dereferencing it unguarded used to throw a
	 * NullPointerException for every pipeline that omitted it.
	 */
	@Test
	public void omittingWaitForCompletionDoesNotPoll() throws Exception {
		this.run("deployTestNoWait",
				"applicationName: 'app', deploymentGroupName: 'group', s3Bucket: 'b', s3Key: 'k', s3BundleType: 'zip'",
				Result.SUCCESS);

		Mockito.verify(this.codeDeploy, Mockito.never()).getDeployment(Mockito.any(GetDeploymentRequest.class));
	}

	@Test
	public void waitForCompletionPollsUntilTheDeploymentSucceeds() throws Exception {
		Mockito.when(this.codeDeploy.getDeployment(Mockito.any(GetDeploymentRequest.class)))
				.thenReturn(GetDeploymentResponse.builder()
						.deploymentInfo(DeploymentInfo.builder().status("Succeeded").build())
						.build());

		this.run("deployTestWait",
				"applicationName: 'app', deploymentGroupName: 'group', s3Bucket: 'b', s3Key: 'k', s3BundleType: 'zip', waitForCompletion: true",
				Result.SUCCESS);

		Mockito.verify(this.codeDeploy).getDeployment(GetDeploymentRequest.builder().deploymentId("d-1").build());
	}

	/**
	 * v1 rejected an unrecognised value outright, naming it. v2's fromValue returns a sentinel
	 * whose value is null, which would otherwise be sent to AWS as fileExistsBehavior=null and
	 * fail there with an opaque message after the deployment call is already in flight.
	 */
	@Test
	public void rejectsAnUnknownFileExistsBehavior() throws Exception {
		WorkflowRun run = this.run("deployTestBadBehavior",
				"applicationName: 'app', deploymentGroupName: 'group', s3Bucket: 'b', s3Key: 'k', s3BundleType: 'zip', fileExistsBehavior: 'OVERWRTIE'",
				Result.FAILURE);

		this.jenkinsRule.assertLogContains("OVERWRTIE", run);
		Mockito.verify(this.codeDeploy, Mockito.never()).createDeployment(Mockito.any(CreateDeploymentRequest.class));
	}

	/**
	 * Validation runs before the compute-platform check, so a typo fails the same way regardless
	 * of whether the deployment group turns out to be ECS.
	 */
	@Test
	public void rejectsAnUnknownFileExistsBehaviorForEcsDeploymentsToo() throws Exception {
		Mockito.when(this.codeDeploy.getDeploymentGroup(Mockito.any(GetDeploymentGroupRequest.class)))
				.thenReturn(GetDeploymentGroupResponse.builder()
						.deploymentGroupInfo(DeploymentGroupInfo.builder().computePlatform("ECS").build())
						.build());

		WorkflowRun run = this.run("deployTestEcsBadBehavior",
				"applicationName: 'app', deploymentGroupName: 'group', s3Bucket: 'b', s3Key: 'k', s3BundleType: 'zip', fileExistsBehavior: 'OVERWRTIE'",
				Result.FAILURE);

		this.jenkinsRule.assertLogContains("OVERWRTIE", run);
		Mockito.verify(this.codeDeploy, Mockito.never()).createDeployment(Mockito.any(CreateDeploymentRequest.class));
	}

	/**
	 * ECS and Lambda deployments must not carry fileExistsBehavior at all.
	 */
	@Test
	public void omitsFileExistsBehaviorForEcsDeployments() throws Exception {
		Mockito.when(this.codeDeploy.getDeploymentGroup(Mockito.any(GetDeploymentGroupRequest.class)))
				.thenReturn(GetDeploymentGroupResponse.builder()
						.deploymentGroupInfo(DeploymentGroupInfo.builder().computePlatform("ECS").build())
						.build());

		this.run("deployTestEcs",
				"applicationName: 'app', deploymentGroupName: 'group', s3Bucket: 'b', s3Key: 'k', s3BundleType: 'zip'",
				Result.SUCCESS);

		ArgumentCaptor<CreateDeploymentRequest> captor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
		Mockito.verify(this.codeDeploy).createDeployment(captor.capture());
		assertThat(captor.getValue().fileExistsBehavior()).isNull();
	}
}
