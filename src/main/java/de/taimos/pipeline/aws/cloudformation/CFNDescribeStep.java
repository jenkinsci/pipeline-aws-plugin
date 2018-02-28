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

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFNDescribeStep extends Step {

	private final String stack;

	@DataBoundConstructor
	public CFNDescribeStep(String stack) {
		this.stack = stack;
	}

	public String getStack() {
		return this.stack;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNDescribeStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
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

	public static class Execution extends StepExecution {

		private transient final CFNDescribeStep step;

		public Execution(CFNDescribeStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public boolean start() throws Exception {
			final String stack = this.step.getStack();
			final TaskListener listener = this.getContext().get(TaskListener.class);

			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");

			listener.getLogger().format("Getting outputs of CloudFormation stack %s %n", stack);

			new Thread("cfnDescribe-" + stack) {
				@Override
				public void run() {
					AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.getContext());
					CloudFormationStack cfnStack = new CloudFormationStack(client, stack, listener);
					try {
						if (cfnStack.exists()) {
							Execution.this.getContext().onSuccess(cfnStack.describeOutputs());
						} else {
							Execution.this.getContext().onSuccess(null);
						}
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
