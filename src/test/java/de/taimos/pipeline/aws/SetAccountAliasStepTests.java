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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateAccountAliasRequest;
import software.amazon.awssdk.services.iam.model.ListAccountAliasesResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The alias guard is the one part of this step that is not a mechanical rename: v1 checked for a
 * null or empty list before comparing, v2 returns an auto-construct list instead. An inverted guard
 * would either re-create an alias that is already correct or skip setting one that is missing, and
 * nothing else in the suite would notice.
 */
@WithJenkins
class SetAccountAliasStepTests {

	private JenkinsRule jenkinsRule;
	private IamClient iam;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.iam = mock(IamClient.class);
		AWSClientFactory.setFactoryDelegate(x -> this.iam);
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void runStep(String jobName) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  setAccountAlias(name: 'my-alias')
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	void createsTheAliasWhenNoneIsSet() throws Exception {
		when(this.iam.listAccountAliases()).thenReturn(ListAccountAliasesResponse.builder().build());

		this.runStep("aliasEmpty");

		ArgumentCaptor<CreateAccountAliasRequest> captor = ArgumentCaptor.forClass(CreateAccountAliasRequest.class);
		verify(this.iam).createAccountAlias(captor.capture());
		assertThat(captor.getValue().accountAlias()).isEqualTo("my-alias");
	}

	@Test
	void createsTheAliasWhenADifferentOneIsSet() throws Exception {
		when(this.iam.listAccountAliases())
				.thenReturn(ListAccountAliasesResponse.builder().accountAliases("other-alias").build());

		this.runStep("aliasDifferent");

		verify(this.iam).createAccountAlias(any(CreateAccountAliasRequest.class));
	}

	@Test
	void doesNothingWhenTheAliasAlreadyMatches() throws Exception {
		when(this.iam.listAccountAliases())
				.thenReturn(ListAccountAliasesResponse.builder().accountAliases("my-alias").build());

		this.runStep("aliasMatches");

		verify(this.iam, never()).createAccountAlias(any(CreateAccountAliasRequest.class));
	}
}
