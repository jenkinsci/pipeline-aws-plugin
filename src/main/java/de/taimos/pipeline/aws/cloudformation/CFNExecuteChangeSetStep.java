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

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
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

import java.io.Serial;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class CFNExecuteChangeSetStep extends Step {

	private final String changeSet;
	private final String stack;
	private PollConfiguration pollConfiguration = PollConfiguration.DEFAULT;

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

	public PollConfiguration getPollConfiguration() {
		return this.pollConfiguration;
	}

	@DataBoundSetter
	public void setPollInterval(Long pollInterval) {
		this.pollConfiguration = this.pollConfiguration.toBuilder()
				.pollInterval(Duration.ofMillis(pollInterval))
				.build();
	}

	@DataBoundSetter
	public void setTimeoutInSeconds(long timeout) {
		this.pollConfiguration = this.pollConfiguration.toBuilder()
				.timeout(Duration.ofSeconds(timeout))
				.build();
	}

	@DataBoundSetter
	public void setTimeoutInMinutes(long timeout) {
		this.pollConfiguration = this.pollConfiguration.toBuilder()
				.timeout(Duration.ofMinutes(timeout))
				.build();
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

	public static class Execution extends SynchronousNonBlockingStepExecution<Map<String, String>> {

		private final transient CFNExecuteChangeSetStep step;

		public Execution(CFNExecuteChangeSetStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public Map<String, String> run() throws Exception {
			final String changeSet = this.step.getChangeSet();
			final String stack = this.step.getStack();
			final TaskListener listener = this.getContext().get(TaskListener.class);

			Preconditions.checkArgument(changeSet != null && !changeSet.isEmpty(), "Change Set must not be null or empty");

			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");

			listener.getLogger().format("Executing CloudFormation change set %s %n", changeSet);

			AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.getContext());
			CloudFormationStack cfnStack = AWSUtilFactory.newCFStack(client, stack, listener);
			Map<String, String> outputs = cfnStack.executeChangeSet(changeSet, Execution.this.step.getPollConfiguration());
			listener.getLogger().println("Execute change set complete");
			return outputs;
		}

		@Serial
		private static final long serialVersionUID = 1L;

	}

}
