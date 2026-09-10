/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2017 Taimos GmbH
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.ListAccountsForParentRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class ListAWSAccountsStep extends Step {

	private String parent;

	@DataBoundConstructor
	public ListAWSAccountsStep() {
		//
	}

	@DataBoundSetter
	public void setParent(String parent) {
		this.parent = parent;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new ListAWSAccountsStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "listAWSAccounts";
		}

		@Override
		public String getDisplayName() {
			return "List all AWS accounts of the organization";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<List> {

		private final transient ListAWSAccountsStep step;

		public Execution(ListAWSAccountsStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected List run() throws Exception {
			this.getContext().get(TaskListener.class).getLogger().format("Getting AWS accounts %n");

			OrganizationsClient client = AWSClientFactory.create(OrganizationsClient.builder(), Execution.this.getContext());
			List<Account> accounts = this.getAccounts(client, this.step.parent);

			return accounts.stream().map(account -> {
				Map<String, String> awsAccount = new HashMap<>();
				awsAccount.put("id", account.id());
				awsAccount.put("arn", account.arn());
				awsAccount.put("name", account.name());
				awsAccount.put("safeName", SafeNameCreator.createSafeName(account.name()));
				// AWS retires Account.Status on 9 September 2026 in favour of Account.State; the SDK
				// documents both as currently available. Each key falls back to the other so that
				// neither goes null for a response that carries only one.
				//
				// Status's values are a subset of State's, so a state filled from status is always a
				// legal state, while a status filled from state can carry PENDING_ACTIVATION or CLOSED.
				//
				// AsString rather than the enum accessors: the pipeline-visible value is the raw string
				// such as ACTIVE, which is what status has always reported. For a value this SDK models
				// the enum stringifies to the same thing, so the difference only shows on one it does
				// not - there the enum accessor yields UNKNOWN_TO_SDK_VERSION, rendering as the literal
				// "null", while AsString still returns what the service sent. Same trap as
				// S3UploadOptions describes and EBWaitOnEnvironmentStatusStepTest pins.
				String state = account.stateAsString();
				String status = account.statusAsString();
				awsAccount.put("state", state != null ? state : status);
				awsAccount.put("status", status != null ? status : state);
				return awsAccount;
			}).collect(Collectors.toList());
		}

		/**
		 * The v1 implementation walked nextToken by hand and recursed; v2 supplies paginators that
		 * issue the same sequence of calls.
		 */
		private List<Account> getAccounts(OrganizationsClient client, String parent) {
			if (parent != null) {
				return client.listAccountsForParentPaginator(ListAccountsForParentRequest.builder().parentId(parent).build())
						.stream()
						.flatMap(page -> page.accounts().stream())
						.collect(Collectors.toList());
			}
			return client.listAccountsPaginator(ListAccountsRequest.builder().build())
					.stream()
					.flatMap(page -> page.accounts().stream())
					.collect(Collectors.toList());
		}

		private static final long serialVersionUID = 1L;

	}

	public static class SafeNameCreator {

		private SafeNameCreator() {
			// hidden constructor
		}

		public static String createSafeName(String name) {
			return name.replaceAll("[^A-Za-z0-9-]", "-").replaceAll("-+", "-").toLowerCase();
		}

	}

}
