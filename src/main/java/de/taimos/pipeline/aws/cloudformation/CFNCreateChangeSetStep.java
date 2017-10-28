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

import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;
import com.google.common.base.Preconditions;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.util.Collection;

public class CFNCreateChangeSetStep extends AbstractCFNCreateStep {

	private final String changeSet;

	@DataBoundConstructor
	public CFNCreateChangeSetStep(String changeSet, String stack) {
		super(stack);
		this.changeSet = changeSet;
	}

	public String getChangeSet() {
		return this.changeSet;
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "cfnCreateChangeSet";
		}
		
		@Override
		public String getDisplayName() {
			return "Create CloudFormation change set";
		}
	}
	
	public static class Execution extends AbstractCFNCreateStep.Execution {
		
		@Inject
		private transient CFNCreateChangeSetStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;

		@Override
		public AbstractCFNCreateStep getStep() {
			return this.step;
		}

		@Override
		public EnvVars getEnvVars() {
			return this.envVars;
		}

		@Override
		public FilePath getWorkspace() {
			return this.workspace;
		}

		@Override
		public TaskListener getListener() {
			return this.listener;
		}

		@Override
		public void checkPreconditions() {
			final String changeSet = this.step.getChangeSet();
			Preconditions.checkArgument(changeSet != null && !changeSet.isEmpty(), "Change Set must not be null or empty");
		}

		@Override
		public String getThreadName() {
			return "cfnCreateChangeSet-" + this.step.getChangeSet();
		}

		@Override
		public Object whenStackExists(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception {
			final String changeSet = this.step.getChangeSet();
			final String file = this.step.getFile();
			final String url = this.step.getUrl();
			this.getCfnStack().createChangeSet(changeSet, this.readTemplate(file), url, parameters, tags, this.getStep().getPollInterval(), ChangeSetType.UPDATE, this.getStep().getRoleArn());
			return null;
		}

		@Override
		public Object whenStackMissing(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception {
			final String changeSet = this.step.getChangeSet();
			final String file = this.step.getFile();
			final String url = this.step.getUrl();
			this.getCfnStack().createChangeSet(changeSet, this.readTemplate(file), url, parameters, tags, this.getStep().getPollInterval(), ChangeSetType.CREATE, this.getStep().getRoleArn());
			return null;
		}

		private static final long serialVersionUID = 1L;

	}
	
}
