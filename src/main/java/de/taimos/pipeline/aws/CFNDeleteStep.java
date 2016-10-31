/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
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

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.waiters.WaiterParameters;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFNDeleteStep extends AbstractStepImpl {
	
	private final String stack;
	
	@DataBoundConstructor
	public CFNDeleteStep(String stack) {
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
			return "cfnDelete";
		}
		
		@Override
		public String getDisplayName() {
			return "Delete CloudFormation stack";
		}
	}
	
	public static class Execution extends AbstractSynchronousStepExecution<Void> {
		
		@Inject
		private transient CFNDeleteStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		protected Void run() throws Exception {
			AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, this.envVars);
			
			String stack = this.step.getStack();
			
			this.listener.getLogger().format("Removing CloudFormation stack %s %n", stack);
			
			client.deleteStack(new DeleteStackRequest().withStackName(stack));
			
			DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest().withStackName(stack);
			client.waiters().stackDeleteComplete().run(new WaiterParameters<>(describeStacksRequest));
			
			this.listener.getLogger().println("Stack deletion complete");
			return null;
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
