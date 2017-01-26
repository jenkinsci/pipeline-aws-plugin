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

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFNDescribeStep extends AbstractStepImpl {
	
	private final String stack;
	
	@DataBoundConstructor
	public CFNDescribeStep(String stack) {
		this.stack = stack;
	}
	
	public String getStack() {
		return this.stack;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "cfnDescribe";
		}
		
		@Override
		public String getDisplayName() {
			return "Describe outputs of CloudFormation stack";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient CFNDescribeStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String stack = this.step.getStack();
			
			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");
			
			this.listener.getLogger().format("Getting outputs of CloudFormation stack %s %n", stack);
			
			new Thread("cfnDescribe-" + stack) {
				@Override
				public void run() {
					AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, Execution.this.envVars);
					CloudFormationStack cfnStack = new CloudFormationStack(client, stack, Execution.this.listener);
					try {
						Execution.this.getContext().onSuccess(cfnStack.describeOutputs());
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
	
}
