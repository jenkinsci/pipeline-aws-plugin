/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2018 Taimos GmbH
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

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.UpdateAssumeRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.UpdateAssumeRolePolicyResult;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class UpdateTrustPolicyStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonIdentityManagement iam;

	@Before
	public void setupSdk() throws Exception {
		this.iam = Mockito.mock(AmazonIdentityManagement.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.iam);
	}

	@Test
	public void updateTrustPolicy() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "updateTest");
		Mockito.when(this.iam.updateAssumeRolePolicy(Mockito.any(UpdateAssumeRolePolicyRequest.class))).thenReturn(new UpdateAssumeRolePolicyResult());
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  writeFile(file: 'testfile', text: '{}')\n"
														+ "  updateTrustPolicy(roleName: 'testRole', policyFile: 'testfile')\n"
														+ "}\n", true)
		);

		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(this.iam).updateAssumeRolePolicy(new UpdateAssumeRolePolicyRequest().withRoleName("testRole").withPolicyDocument("{}"));
	}

}
