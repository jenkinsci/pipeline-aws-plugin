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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.LaunchPermission;
import com.amazonaws.services.ec2.model.LaunchPermissionModifications;
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class EC2ShareAmiStep extends Step {

	private List<String> accountIds;
	private String amiId;

	@DataBoundConstructor
	public EC2ShareAmiStep() {
		//
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new EC2ShareAmiStep.Execution(this, context);
	}

	public List<String> getAccountIds() {
		return this.accountIds;
	}

	@DataBoundSetter
	public void setAccountIds(List<String> accountIds) {
		this.accountIds = accountIds;
	}

	public String getAmiId() {
		return this.amiId;
	}

	@DataBoundSetter
	public void setAmiId(String amiId) {
		this.amiId = amiId;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "ec2ShareAmi";
		}

		@Override
		public String getDisplayName() {
			return "Share an AMI with other accounts";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<String> {

		private final transient EC2ShareAmiStep step;

		public Execution(EC2ShareAmiStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected String run() throws Exception {

			TaskListener listener = this.getContext().get(TaskListener.class);

			listener.getLogger().println("Sharing amiId=" + this.step.amiId + " to accounts: " + this.step.accountIds);

			AmazonEC2 ec2 = AWSClientFactory.create(AmazonEC2ClientBuilder.standard(), this.getContext());
			ec2.modifyImageAttribute(new ModifyImageAttributeRequest()
					.withImageId(this.step.amiId)
					.withLaunchPermission(new LaunchPermissionModifications()
							.withAdd(this.step.accountIds.stream().map(accountId -> new LaunchPermission().withUserId(accountId)).collect(Collectors.toList()))
					)
			);
			listener.getLogger().println("Shared amiId=" + this.step.amiId + " to accounts: " + this.step.accountIds);
			return null;
		}

		private static final long serialVersionUID = 1L;

	}

}
