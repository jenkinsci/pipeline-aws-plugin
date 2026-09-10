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

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.ListAccountsForParentRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsForParentResponse;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsResponse;
import software.amazon.awssdk.services.organizations.paginators.ListAccountsForParentIterable;
import software.amazon.awssdk.services.organizations.paginators.ListAccountsIterable;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the map keys that {@code listAWSAccounts} returns to the pipeline, and the pagination
 * contract. The keys are built by hand in the step, but the values and the paging tokens come
 * straight off the SDK model, so this guards the mapping through an SDK upgrade.
 */
public class ListAWSAccountsStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private OrganizationsClient organizations;

	@Before
	public void setupSdk() throws Exception {
		this.organizations = Mockito.mock(OrganizationsClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.organizations);

		// The paginator methods are defaults on the client interface, so a mock returns null for
		// them. Handing back a real paginator over the mock keeps the assertions below meaningful:
		// the SDK's own paging logic issues the underlying calls, so what is captured is what the
		// SDK really sends, including the token it carries between pages.
		Mockito.when(this.organizations.listAccountsPaginator(Mockito.any(ListAccountsRequest.class)))
				.thenAnswer(invocation -> new ListAccountsIterable(this.organizations, invocation.getArgument(0)));
		Mockito.when(this.organizations.listAccountsForParentPaginator(Mockito.any(ListAccountsForParentRequest.class)))
				.thenAnswer(invocation -> new ListAccountsForParentIterable(this.organizations, invocation.getArgument(0)));
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private static Account account(String id, String name) {
		return Account.builder()
				.id(id)
				.arn("arn:aws:organizations::123456789012:account/o-exampleorg/" + id)
				.name(name)
				// Both, which the SDK documents as the current shape.
				.status("ACTIVE")
				.state("ACTIVE")
				.build();
	}

	@Test
	public void listAccountsExposesEveryKey() throws Exception {
		Mockito.when(this.organizations.listAccounts(Mockito.any(ListAccountsRequest.class))).thenReturn(ListAccountsResponse.builder()
				.accounts(account("111111111111", "My Account"))
				.build()
		);

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts()\n"
				+ "  echo \"accountsCount=${accounts.size()}\"\n"
				+ "  echo \"id=${accounts[0].id}\"\n"
				+ "  echo \"arn=${accounts[0].arn}\"\n"
				+ "  echo \"name=${accounts[0].name}\"\n"
				+ "  echo \"safeName=${accounts[0].safeName}\"\n"
				+ "  echo \"status=${accounts[0].status}\"\n"
				+ "  echo \"state=${accounts[0].state}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("accountsCount=1", run);
		this.jenkinsRule.assertLogContains("id=111111111111", run);
		this.jenkinsRule.assertLogContains("arn=arn:aws:organizations::123456789012:account/o-exampleorg/111111111111", run);
		this.jenkinsRule.assertLogContains("name=My Account", run);
		this.jenkinsRule.assertLogContains("safeName=my-account", run);
		this.jenkinsRule.assertLogContains("status=ACTIVE", run);
		this.jenkinsRule.assertLogContains("state=ACTIVE", run);
	}

	/**
	 * A response carrying State but not Status - what a stub or a non-AWS endpoint can return today,
	 * and the shape the Status retirement points toward. The fallback keys off the response either
	 * way, so the documented status key does not go null.
	 */
	@Test
	public void statusFallsBackToStateWhenTheResponseOmitsStatus() throws Exception {
		Mockito.when(this.organizations.listAccounts(Mockito.any(ListAccountsRequest.class))).thenReturn(ListAccountsResponse.builder()
				.accounts(Account.builder()
						.id("222222222222")
						.arn("arn:aws:organizations::123456789012:account/o-exampleorg/222222222222")
						.name("State Only")
						// No status. CLOSED is also a value Status could never carry, so this pins that
						// the new set comes through rather than being clamped to the legacy one.
						.state("CLOSED")
						.build())
				.build()
		);

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsStateFallback");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts()\n"
				+ "  echo \"status=${accounts[0].status}\"\n"
				+ "  echo \"state=${accounts[0].state}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("status=CLOSED", run);
		this.jenkinsRule.assertLogContains("state=CLOSED", run);
	}

	/**
	 * Which model field feeds which key, pinned by making them disagree: an unconditional
	 * status = stateAsString() would publish a State-only value such as PENDING_ACTIVATION under
	 * status even for a response that supplied a perfectly good Status.
	 */
	@Test
	public void statusAndStateReportTheirOwnFieldWhenTheyDisagree() throws Exception {
		Mockito.when(this.organizations.listAccounts(Mockito.any(ListAccountsRequest.class))).thenReturn(ListAccountsResponse.builder()
				.accounts(Account.builder()
						.id("333333333333")
						.arn("arn:aws:organizations::123456789012:account/o-exampleorg/333333333333")
						.name("Newly Created")
						.status("ACTIVE")
						.state("PENDING_ACTIVATION")
						.build())
				.build()
		);

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsDivergent");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts()\n"
				+ "  echo \"status=${accounts[0].status}\"\n"
				+ "  echo \"state=${accounts[0].state}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("status=ACTIVE", run);
		this.jenkinsRule.assertLogContains("state=PENDING_ACTIVATION", run);
	}

	/**
	 * The mirror: a response carrying Status but not State, which an endpoint that does not model
	 * State yet returns today. The state key, which the README recommends over status, must not be
	 * the only one that can come back null.
	 */
	@Test
	public void stateFallsBackToStatusWhenTheResponseOmitsState() throws Exception {
		Mockito.when(this.organizations.listAccounts(Mockito.any(ListAccountsRequest.class))).thenReturn(ListAccountsResponse.builder()
				.accounts(Account.builder()
						.id("444444444444")
						.arn("arn:aws:organizations::123456789012:account/o-exampleorg/444444444444")
						.name("Old Endpoint")
						.status("SUSPENDED")
						.build())
				.build()
		);

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsStatusOnly");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts()\n"
				+ "  echo \"status=${accounts[0].status}\"\n"
				+ "  echo \"state=${accounts[0].state}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("status=SUSPENDED", run);
		this.jenkinsRule.assertLogContains("state=SUSPENDED", run);
	}

	/**
	 * The second page must be requested with the token returned by the first. Asserting only the
	 * call count would stay green if the token stopped being propagated, since the mock returns
	 * the second page regardless of what it is asked for.
	 */
	@Test
	public void listAccountsPropagatesPagingToken() throws Exception {
		Mockito.when(this.organizations.listAccounts(Mockito.any(ListAccountsRequest.class)))
				.thenReturn(ListAccountsResponse.builder()
						.accounts(account("111111111111", "First"))
						.nextToken("next")
						.build())
				.thenReturn(ListAccountsResponse.builder()
						.accounts(account("222222222222", "Second"))
						.build());

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsPagingTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts()\n"
				+ "  echo \"ids=${accounts.collect { it.id }.toString()}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("ids=[111111111111, 222222222222]", run);

		ArgumentCaptor<ListAccountsRequest> captor = ArgumentCaptor.forClass(ListAccountsRequest.class);
		Mockito.verify(this.organizations, Mockito.times(2)).listAccounts(captor.capture());
		List<ListAccountsRequest> requests = captor.getAllValues();
		assertThat(requests.get(0).nextToken()).isNull();
		assertThat(requests.get(1).nextToken()).isEqualTo("next");
	}

	@Test
	public void listAccountsForParentPropagatesParentAndPagingToken() throws Exception {
		Mockito.when(this.organizations.listAccountsForParent(Mockito.any(ListAccountsForParentRequest.class)))
				.thenReturn(ListAccountsForParentResponse.builder()
						.accounts(account("111111111111", "First"))
						.nextToken("next")
						.build())
				.thenReturn(ListAccountsForParentResponse.builder()
						.accounts(account("222222222222", "Second"))
						.build());

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "listAccountsParentTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def accounts = listAWSAccounts(parent: 'ou-1234')\n"
				+ "  echo \"accountsCount=${accounts.size()}\"\n"
				+ "  echo \"ids=${accounts.collect { it.id }.toString()}\"\n"
				+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("accountsCount=2", run);
		this.jenkinsRule.assertLogContains("ids=[111111111111, 222222222222]", run);

		ArgumentCaptor<ListAccountsForParentRequest> captor = ArgumentCaptor.forClass(ListAccountsForParentRequest.class);
		Mockito.verify(this.organizations, Mockito.times(2)).listAccountsForParent(captor.capture());
		List<ListAccountsForParentRequest> requests = captor.getAllValues();
		assertThat(requests.get(0).parentId()).isEqualTo("ou-1234");
		assertThat(requests.get(0).nextToken()).isNull();
		assertThat(requests.get(1).parentId()).isEqualTo("ou-1234");
		assertThat(requests.get(1).nextToken()).isEqualTo("next");
	}
}
