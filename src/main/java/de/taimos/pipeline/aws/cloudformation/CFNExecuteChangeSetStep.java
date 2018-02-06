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

import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFNExecuteChangeSetStep extends Step {
	
	private final String changeSet;
	private final String stack;
	private Long pollInterval = 1000L;
	
	@DataBoundConstructor
	public CFNExecuteChangeSetStep(String changeSet, String stack) {
		this.changeSet = changeSet;
		this.stack = stack;
	}
	
	public String getChangeSet() {
		return this.changeSet;
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

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNExecuteChangeSetStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "cfnExecuteChangeSet";
		}
		
		@Override
		public String getDisplayName() {
			return "Execute CloudFormation change set";
		}
	}
	
	public static class Execution extends StepExecution {
		
		private final transient CFNExecuteChangeSetStep step;

		public Execution(CFNExecuteChangeSetStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public boolean start() throws Exception {
			final String changeSet = this.step.getChangeSet();
			final String stack = this.step.getStack();
			final TaskListener listener = this.getContext().get(TaskListener.class);
			
			Preconditions.checkArgument(changeSet != null && !changeSet.isEmpty(), "Change Set must not be null or empty");
			
			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");
			
			listener.getLogger().format("Executing CloudFormation change set %s %n", changeSet);
			
			new Thread("cfnExecuteChangeSet-" + changeSet) {
				@Override
				public void run() {
					try {
						AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.getContext());
						CloudFormationStack cfnStack = new CloudFormationStack(client, stack, listener);
						cfnStack.executeChangeSet(changeSet, Execution.this.step.getPollInterval());
						listener.getLogger().println("Execute change set complete");
						Execution.this.getContext().onSuccess(cfnStack.describeOutputs());
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
