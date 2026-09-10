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

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class CFNDeleteStackSetStep extends Step {

	private final String stackSet;
	@Deprecated
	private Long pollInterval;

	@DataBoundConstructor
	public CFNDeleteStackSetStep(String stackSet) {
		this.stackSet = stackSet;
	}

	public String getStackSet() {
		return this.stackSet;
	}

	/**
	 * Never read: this step submits deleteStackSet and returns without waiting, so there is nothing
	 * to poll. Kept so that existing pipelines passing pollInterval keep binding instead of failing
	 * with a "no such property" error, and deprecated to flag it as dead to anyone reading the class.
	 * Dropping the 1000 ms default also stops uninstantiate from writing pollInterval: 1000 into every
	 * snippet the generator produces for this step.
	 */
	@Deprecated
	public Long getPollInterval() {
		return this.pollInterval;
	}

	@Deprecated
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

			CloudFormationClient client = AWSClientFactory.create(CloudFormationClient.builder(), Execution.this.getContext());
			CloudFormationStackSet cfnStackSet = AWSUtilFactory.newCFStackSet(client, stackSet, listener, SleepStrategy.EXPONENTIAL_BACKOFF_STRATEGY);
			cfnStackSet.delete();
			listener.getLogger().println("Stack Set deletion complete");
			return null;
		}

		private static final long serialVersionUID = 1L;

	}

}
