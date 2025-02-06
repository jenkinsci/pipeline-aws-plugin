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

package de.taimos.pipeline.aws.cloudformation.stacksets;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Set;

public class CFNDeleteStackSetStep extends Step {

	private final String stackSet;
	private Long pollInterval = 1000L;

	@DataBoundConstructor
	public CFNDeleteStackSetStep(String stackSet) {
		this.stackSet = stackSet;
	}

	public String getStackSet() {
		return this.stackSet;
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
		return new CFNDeleteStackSetStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "cfnDeleteStackSet";
		}

		@Override
		@NonNull
		public String getDisplayName() {
			return "Delete CloudFormation Stack Set";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

		private transient CFNDeleteStackSetStep step;

		public Execution(CFNDeleteStackSetStep step, @NonNull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public Void run() throws Exception {
			final String stackSet = this.step.getStackSet();
			final TaskListener listener = this.getContext().get(TaskListener.class);

			Preconditions.checkArgument(stackSet != null && !stackSet.isEmpty(), "StackSet must not be null or empty");

			listener.getLogger().format("Removing CloudFormation stack set %s %n", stackSet);

			AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.getContext());
			CloudFormationStackSet cfnStackSet = AWSUtilFactory.newCFStackSet(client, stackSet, listener, SleepStrategy.EXPONENTIAL_BACKOFF_STRATEGY);
			cfnStackSet.delete();
			listener.getLogger().println("Stack Set deletion complete");
			return null;
		}

		private static final long serialVersionUID = 1L;

	}

}
