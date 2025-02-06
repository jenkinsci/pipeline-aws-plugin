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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.RollbackConfiguration;
import com.amazonaws.services.cloudformation.model.Tag;

import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class CFNUpdateStep extends AbstractCFNCreateStep {

	private Boolean enableTerminationProtection;

	@DataBoundConstructor
	public CFNUpdateStep(String stack) {
		super(stack);
	}

	public Boolean getEnableTerminationProtection() {
		return this.enableTerminationProtection;
	}

	@DataBoundSetter
	public void setEnableTerminationProtection(Boolean enableTerminationProtection) {
		this.enableTerminationProtection = enableTerminationProtection;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNUpdateStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(EnvVars.class, TaskListener.class, FilePath.class);
		}

		@Override
		public String getFunctionName() {
			return "cfnUpdate";
		}

		@Override
		public String getDisplayName() {
			return "Create or Update CloudFormation stack";
		}
	}

	public static class Execution extends AbstractCFNCreateStep.Execution<CFNUpdateStep, Map<String, String>> {

		protected Execution(CFNUpdateStep step, @NonNull StepContext context) {
			super(step, context);
		}

		@Override
		public void checkPreconditions() {
			// nothing to do here
		}

		@Override
		public Map<String, String> whenStackExists(Collection<Parameter> parameters, Collection<Tag> tags, Collection<String> notificationARNs, RollbackConfiguration rollbackConfiguration) throws Exception {
			final String url = this.getStep().getUrl();
			CloudFormationStack cfnStack = this.getCfnStack();
			return cfnStack.update(this.getStep().readTemplate(this), url, parameters, tags, notificationARNs, this.getStep().getPollConfiguration(), this.getStep().getRoleArn(), rollbackConfiguration);
		}

		@Override
		public Map<String, String> whenStackMissing(Collection<Parameter> parameters, Collection<Tag> tags, Collection<String> notificationARNs) throws Exception {
			final String url = this.getStep().getUrl();
			CloudFormationStack cfnStack = this.getCfnStack();
			return cfnStack.create(this.getStep().readTemplate(this), url, parameters, tags, notificationARNs, this.getStep().getPollConfiguration(), this.getStep().getRoleArn(), this.getStep().getOnFailure(), this.getStep().getEnableTerminationProtection());
		}

		private static final long serialVersionUID = 1L;

	}

}
