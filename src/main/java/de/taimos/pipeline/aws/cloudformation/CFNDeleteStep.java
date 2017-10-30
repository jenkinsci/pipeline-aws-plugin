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

package de.taimos.pipeline.aws.cloudformation;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFNDeleteStep extends AbstractStepImpl {

	private final String stack;
	private Long pollInterval = 1000L;

	@DataBoundConstructor
	public CFNDeleteStep(String stack) {
		this.stack = stack;
	}

	public String getStack() {
		return this.stack;
	}

	public Long getPollInterval() {
		return this.pollInterval;
	}

	@DataBoundSetter
	public void setPollInterval(Long pollInterval) {
		this.pollInterval = pollInterval;
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(Execution.class);
		}

		@Override
		public String getFunctionName() {
			return "cfnDelete";
		}

		@Override
		public String getDisplayName() {
			return "Delete CloudFormation stack";
		}
	}

	public static class Execution extends AbstractStepExecutionImpl {

		@Inject
		private transient CFNDeleteStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;

		@Override
		public boolean start() throws Exception {
			final String stack = this.step.getStack();

			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");

			this.listener.getLogger().format("Removing CloudFormation stack %s %n", stack);

			new Thread("cfnDelete-" + stack) {
				@Override
				public void run() {
					try {
						AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.envVars);
						CloudFormationStack cfnStack = new CloudFormationStack(client, stack, Execution.this.listener);
						cfnStack.delete(Execution.this.step.getPollInterval());
						Execution.this.listener.getLogger().println("Stack deletion complete");
						Execution.this.getContext().onSuccess(null);
					} catch (Exception e) {
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

}
