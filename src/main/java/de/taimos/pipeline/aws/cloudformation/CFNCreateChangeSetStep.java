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

import com.amazonaws.services.cloudformation.model.Change;
import com.amazonaws.services.cloudformation.model.ChangeSetStatus;
import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.RollbackConfiguration;
import com.amazonaws.services.cloudformation.model.Tag;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNCreateChangeSetStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "cfnCreateChangeSet";
		}

		@Override
		public String getDisplayName() {
			return "Create CloudFormation change set";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(EnvVars.class, TaskListener.class, FilePath.class);
		}
	}

	public static class Execution extends AbstractCFNCreateStep.Execution<CFNCreateChangeSetStep, List<Change>> {

		public Execution(CFNCreateChangeSetStep step, StepContext context) {
			super(step, context);
		}

		@Override
		public void checkPreconditions() {
			final String changeSet = this.getStep().getChangeSet();
			Preconditions.checkArgument(changeSet != null && !changeSet.isEmpty(), "Change Set must not be null or empty");
		}

		@Override
		public List<Change> whenStackExists(Collection<Parameter> parameters, Collection<Tag> tags, Collection<String> notificationARNs, RollbackConfiguration rollbackConfiguration) throws Exception {
			final String changeSet = this.getStep().getChangeSet();
			final String url = this.getStep().getUrl();
			this.getCfnStack().createChangeSet(changeSet, this.getStep().readTemplate(this), url, parameters, tags, notificationARNs, this.getStep().getPollConfiguration(), ChangeSetType.UPDATE, this.getStep().getRoleArn(), rollbackConfiguration);
			return this.validateChangeSet(changeSet);
		}

		@Override
		public List<Change> whenStackMissing(Collection<Parameter> parameters, Collection<Tag> tags, Collection<String> notificationARNs) throws Exception {
			final String changeSet = this.getStep().getChangeSet();
			final String url = this.getStep().getUrl();
			this.getCfnStack().createChangeSet(changeSet, this.getStep().readTemplate(this), url, parameters, tags, notificationARNs, this.getStep().getPollConfiguration(), ChangeSetType.CREATE, this.getStep().getRoleArn(), null);
			return this.validateChangeSet(changeSet);
		}

		private List<Change> validateChangeSet(String changeSet) {
			DescribeChangeSetResult result = this.getCfnStack().describeChangeSet(changeSet);
			if (ChangeSetStatus.CREATE_COMPLETE.name().equals(result.getStatus())) {
				return result.getChanges();
			} else if (ChangeSetStatus.FAILED.name().equals(result.getStatus()) && result.getStatusReason().toLowerCase().contains("the submitted information didn't contain changes")) {
				return result.getChanges();
			} else if (ChangeSetStatus.FAILED.name().equals(result.getStatus()) && result.getStatusReason().toLowerCase().contains("no updates are to be performed")) {
				return result.getChanges();
			} else {
				throw new IllegalStateException("Change set did not create successfully. status=" + result.getStatus() + " reason=" + result.getStatusReason());
			}
		}

		@Serial
		private static final long serialVersionUID = 1L;

	}

}
