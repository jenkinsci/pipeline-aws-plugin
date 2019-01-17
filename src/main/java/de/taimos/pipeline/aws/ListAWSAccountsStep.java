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

import com.amazonaws.services.organizations.AWSOrganizations;
import com.amazonaws.services.organizations.AWSOrganizationsClientBuilder;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.ListAccountsForParentRequest;
import com.amazonaws.services.organizations.model.ListAccountsForParentResult;
import com.amazonaws.services.organizations.model.ListAccountsRequest;
import com.amazonaws.services.organizations.model.ListAccountsResult;

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

			AWSOrganizations client = AWSClientFactory.create(AWSOrganizationsClientBuilder.standard(), Execution.this.getContext());
			List<Account> accounts = this.getAccounts(client, this.step.parent, null);

			return accounts.stream().map(account -> {
				Map<String, String> awsAccount = new HashMap<>();
				awsAccount.put("id", account.getId());
				awsAccount.put("arn", account.getArn());
				awsAccount.put("name", account.getName());
				awsAccount.put("safeName", SafeNameCreator.createSafeName(account.getName()));
				awsAccount.put("status", account.getStatus());
				return awsAccount;
			}).collect(Collectors.toList());
		}

		private List<Account> getAccounts(AWSOrganizations client, String parent, String startToken) {
			final List<Account> accounts;
			final String nextToken;
			if (parent != null) {
				ListAccountsForParentResult result = client.listAccountsForParent(new ListAccountsForParentRequest().withParentId(parent).withNextToken(startToken));
				accounts = result.getAccounts();
				nextToken = result.getNextToken();
			} else {
				ListAccountsResult result = client.listAccounts(new ListAccountsRequest().withNextToken(startToken));
				accounts = result.getAccounts();
				nextToken = result.getNextToken();
			}
			if (nextToken != null) {
				accounts.addAll(this.getAccounts(client, parent, nextToken));
			}
			return accounts;
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
