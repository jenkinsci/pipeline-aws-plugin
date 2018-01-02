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

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.CreateAccountAliasRequest;
import com.amazonaws.services.identitymanagement.model.ListAccountAliasesResult;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class SetAccountAliasStep extends AbstractStepImpl {
	
	private final String name;
	
	@DataBoundConstructor
	public SetAccountAliasStep(String name) {
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "setAccountAlias";
		}
		
		@Override
		public String getDisplayName() {
			return "Set the AWS account alias";
		}
	}
	
	public static class Execution extends AbstractSynchronousStepExecution<Void> {
		
		@Inject
		private transient SetAccountAliasStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		protected Void run() throws Exception {
			String name = this.step.getName();
			AmazonIdentityManagement iamClient = AWSClientFactory.create(AmazonIdentityManagementClientBuilder.standard(), Execution.this.envVars);
			
			Execution.this.listener.getLogger().format("Checking for account alias %s %n", name);
			ListAccountAliasesResult listResult = iamClient.listAccountAliases();
			
			// no or different alias set
			if (listResult.getAccountAliases() == null || listResult.getAccountAliases().isEmpty() || !listResult.getAccountAliases().contains(name)) {
				// Update alias
				iamClient.createAccountAlias(new CreateAccountAliasRequest().withAccountAlias(name));
				Execution.this.listener.getLogger().format("Created account alias %s %n", name);
			} else {
				// Nothing to do
				Execution.this.listener.getLogger().format("Account alias already set %s %n", name);
			}
			return null;
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
