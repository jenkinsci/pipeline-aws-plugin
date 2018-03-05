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

import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.UpdateAssumeRolePolicyRequest;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class UpdateTrustPolicy extends Step {

	private final String roleName;
	private final String policyFile;

	@DataBoundConstructor
	public UpdateTrustPolicy(String roleName, String policyFile) {
		this.roleName = roleName;
		this.policyFile = policyFile;
	}

	public String getRoleName() {
		return this.roleName;
	}

	public String getPolicyFile() {
		return this.policyFile;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new UpdateTrustPolicy.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
		}

		@Override
		public String getFunctionName() {
			return "updateTrustPolicy";
		}

		@Override
		public String getDisplayName() {
			return "Update trust policy of IAM roles";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

		private final transient UpdateTrustPolicy step;

		public Execution(UpdateTrustPolicy step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			final String roleName = this.step.getRoleName();
			final String policyFile = this.step.getPolicyFile();

			Preconditions.checkArgument(roleName != null && !roleName.isEmpty(), "roleName must not be null or empty");
			Preconditions.checkArgument(policyFile != null && !policyFile.isEmpty(), "policyFile must not be null or empty");

			AmazonIdentityManagement iamClient = AWSClientFactory.create(AmazonIdentityManagementClientBuilder.standard(), Execution.this.getContext());

			UpdateAssumeRolePolicyRequest request = new UpdateAssumeRolePolicyRequest();
			request.withRoleName(roleName);
			request.withPolicyDocument(Execution.this.getContext().get(FilePath.class).child(policyFile).readToString());
			iamClient.updateAssumeRolePolicy(request);

			Execution.this.getContext().get(TaskListener.class).getLogger().format("Updated trust policy of role %s %n", roleName);

			return null;
		}

		private static final long serialVersionUID = 1L;

	}

}
