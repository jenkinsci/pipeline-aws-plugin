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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import com.amazonaws.services.organizations.AWSOrganizations;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.organizations.AWSOrganizationsClient;
import com.amazonaws.services.organizations.model.Account;
import com.amazonaws.services.organizations.model.ListAccountsRequest;
import com.amazonaws.services.organizations.model.ListAccountsResult;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class ListAWSAccountsStep extends AbstractStepImpl {

	@DataBoundConstructor
	public ListAWSAccountsStep() {
		//
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(Execution.class);
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

	public static class Execution extends AbstractStepExecutionImpl {

		@Inject
		private transient ListAWSAccountsStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;

		@Override
		public boolean start() throws Exception {
			this.listener.getLogger().format("Getting AWS accounts %n");

			new Thread("listAWSAccounts") {
				@Override
				public void run() {
					AWSOrganizations client = AWSClientFactory.createAWSOrganizationsClient(Execution.this.envVars);
					ListAccountsResult exports = client.listAccounts(new ListAccountsRequest());

					List<Map<String, String>> accounts = new ArrayList<>();
					for (Account account : exports.getAccounts()) {
						Map<String, String> awsAccount = new HashMap<>();
						awsAccount.put("id", account.getId());
						awsAccount.put("arn", account.getArn());
						awsAccount.put("name", account.getName());
						awsAccount.put("safeName", SafeNameCreator.createSafeName(account.getName()));
						awsAccount.put("status", account.getStatus());
						accounts.add(awsAccount);
					}

					try {
						Execution.this.getContext().onSuccess(accounts);
					} catch (AmazonCloudFormationException e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}

		@Override
		public void stop(@Nonnull Throwable cause) throws Exception {
			//
		}

		private static final long serialVersionUID = 1L;

	}

	public static class SafeNameCreator {

		public static String createSafeName(String name) {
			return name.replaceAll("[^A-Za-z0-9-]", "-").replaceAll("-+", "-").toLowerCase();
		}

	}

}
