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
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ecrLogin had no test. It base64-decodes the authorization token, splits it on a colon and formats
 * a docker login command, none of which a compile checks after the accessors were rewritten.
 */
public class ECRLoginStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private EcrClient ecr;

	@Before
	public void setupSdk() throws Exception {
		this.ecr = Mockito.mock(EcrClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.ecr);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void stubToken(String decodedToken) {
		Mockito.when(this.ecr.getAuthorizationToken(Mockito.any(GetAuthorizationTokenRequest.class)))
				.thenReturn(GetAuthorizationTokenResponse.builder()
						.authorizationData(AuthorizationData.builder()
								.authorizationToken(Base64.getEncoder()
										.encodeToString(decodedToken.getBytes(StandardCharsets.UTF_8)))
								.proxyEndpoint("https://1234.dkr.ecr.eu-west-1.amazonaws.com")
								.build())
						.build());
	}

	private WorkflowRun run(String jobName, String args, Result expected) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def cmd = ecrLogin(" + args + ")\n"
				+ "  echo \"cmd=${cmd}\"\n"
				+ "}\n", true)
		);
		return this.jenkinsRule.assertBuildStatus(expected, job.scheduleBuild2(0));
	}

	@Test
	public void buildsADockerLoginCommand() throws Exception {
		this.stubToken("AWS:secret-password");

		WorkflowRun run = this.run("ecrLoginBasic", "", Result.SUCCESS);

		this.jenkinsRule.assertLogContains("docker login -u AWS -p secret-password", run);
		this.jenkinsRule.assertLogContains("https://1234.dkr.ecr.eu-west-1.amazonaws.com", run);
	}

	@Test
	public void addsTheEmailFlagWhenAsked() throws Exception {
		this.stubToken("AWS:secret-password");

		WorkflowRun run = this.run("ecrLoginEmail", "email: true", Result.SUCCESS);

		this.jenkinsRule.assertLogContains("-e none", run);
	}

	/**
	 * registryIds is optional; omitting it must leave the request without one rather than fail.
	 */
	@Test
	public void omitsRegistryIdsWhenNotGiven() throws Exception {
		this.stubToken("AWS:secret-password");

		this.run("ecrLoginNoRegistry", "", Result.SUCCESS);

		ArgumentCaptor<GetAuthorizationTokenRequest> captor = ArgumentCaptor.forClass(GetAuthorizationTokenRequest.class);
		Mockito.verify(this.ecr).getAuthorizationToken(captor.capture());
		// hasRegistryIds, not isEmpty: v2 returns an auto-construct empty list for an unset member,
		// so isEmpty would also pass if the step had sent an explicit empty list
		assertThat(captor.getValue().hasRegistryIds()).isFalse();
	}

	@Test
	public void passesRegistryIdsWhenGiven() throws Exception {
		this.stubToken("AWS:secret-password");

		this.run("ecrLoginRegistry", "registryIds: ['1234', '5678']", Result.SUCCESS);

		ArgumentCaptor<GetAuthorizationTokenRequest> captor = ArgumentCaptor.forClass(GetAuthorizationTokenRequest.class);
		Mockito.verify(this.ecr).getAuthorizationToken(captor.capture());
		assertThat(captor.getValue().registryIds()).containsExactly("1234", "5678");
	}

	@Test
	public void failsWhenAwsReturnsNoAuthorizationData() throws Exception {
		Mockito.when(this.ecr.getAuthorizationToken(Mockito.any(GetAuthorizationTokenRequest.class)))
				.thenReturn(GetAuthorizationTokenResponse.builder().build());

		WorkflowRun run = this.run("ecrLoginNoData", "", Result.FAILURE);

		this.jenkinsRule.assertLogContains("Did not get authorizationData from AWS", run);
	}

	@Test
	public void failsWhenTheTokenIsNotUserColonPassword() throws Exception {
		this.stubToken("not-a-pair");

		WorkflowRun run = this.run("ecrLoginBadToken", "", Result.FAILURE);

		this.jenkinsRule.assertLogContains("Got invalid authorizationData from AWS", run);
	}
}
