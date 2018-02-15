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
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
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

		public DescriptorImpl() {
		}

		@Override
		public String getFunctionName() {
			return "cfnDeleteStackSet";
		}

		@Override
		@Nonnull
		public String getDisplayName() {
			return "Delete CloudFormation Stack Set";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends StepExecution {

		private transient CFNDeleteStackSetStep step;

		public Execution(CFNDeleteStackSetStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public boolean start() throws Exception {
			final String stackSet = this.step.getStackSet();
			final TaskListener listener = this.getContext().get(TaskListener.class);

			Preconditions.checkArgument(stackSet != null && !stackSet.isEmpty(), "StackSet must not be null or empty");

			listener.getLogger().format("Removing CloudFormation stack set %s %n", stackSet);

			new Thread("cfnDeleteStackSet-" + stackSet) {
				@Override
				public void run() {
					try {
						AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.getContext());
						CloudFormationStackSet cfnStackSet = new CloudFormationStackSet(client, stackSet, listener);
						cfnStackSet.delete();
						listener.getLogger().println("Stack Set deletion complete");
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
