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

import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.CreateAccountAliasRequest;
import com.amazonaws.services.identitymanagement.model.ListAccountAliasesResult;

import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.TaskListener;

public class SetAccountAliasStep extends Step {

	private final String name;

	@DataBoundConstructor
	public SetAccountAliasStep(String name) {
		this.name = name;
	}

	public String getName() {
		return this.name;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new SetAccountAliasStep.Execution(this.name, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
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

	public static class Execution extends SynchronousStepExecution<Void> {

		@SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
		private final transient String name;

		public Execution(String name, StepContext context) {
			super(context);
			this.name = name;
		}

		@Override
		protected Void run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			AmazonIdentityManagement iamClient = AWSClientFactory.create(AmazonIdentityManagementClientBuilder.standard(), Execution.this.getContext());

			listener.getLogger().format("Checking for account alias %s %n", this.name);
			ListAccountAliasesResult listResult = iamClient.listAccountAliases();

			// no or different alias set
			if (listResult.getAccountAliases() == null || listResult.getAccountAliases().isEmpty() || !listResult.getAccountAliases().contains(this.name)) {
				// Update alias
				iamClient.createAccountAlias(new CreateAccountAliasRequest().withAccountAlias(this.name));
				listener.getLogger().format("Created account alias %s %n", this.name);
			} else {
				// Nothing to do
				listener.getLogger().format("Account alias already set %s %n", this.name);
			}
			return null;
		}

		private static final long serialVersionUID = 1L;

	}

}
