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

import software.amazon.awssdk.services.cloudformation.model.DescribeStackSetResponse;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.StackInstanceSummary;
import software.amazon.awssdk.services.cloudformation.model.StackSetStatus;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackSetRequest;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackSetResponse;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CFNUpdateStackSetStep extends AbstractCFNCreateStackSetStep {

	@DataBoundConstructor
	public CFNUpdateStackSetStep(String stackSet) {
		super(stackSet);
	}

	private JenkinsStackSetOperationPreferences operationPreferences;
	private BatchingOptions batchingOptions;

	public JenkinsStackSetOperationPreferences getOperationPreferences() {
		return operationPreferences;
	}

	@DataBoundSetter
	public void setOperationPreferences(JenkinsStackSetOperationPreferences operationPreferences) {
		this.operationPreferences = operationPreferences;
	}

	@DataBoundSetter
	public void setBatchingOptions(BatchingOptions batchingOptions) {
		this.batchingOptions = batchingOptions;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "cfnUpdateStackSet";
		}

		@Override
		public String getDisplayName() {
			return "Create or Update CloudFormation Stack Set";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNUpdateStackSetStep.Execution(this, context);
	}

	public static class Execution extends AbstractCFNCreateStackSetStep.Execution<CFNUpdateStackSetStep> {

		protected Execution(CFNUpdateStackSetStep step, @NonNull StepContext context) {
			super(step, context);
		}

		@Override
		public void checkPreconditions() {
			// Nothing to check here
		}

		@Override
		public String getThreadName() {
			return "cfnUpdateStackSet-" + getStep().getStackSet();
		}

		@Override
		public DescribeStackSetResponse whenStackSetExists(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception {
			final String url = this.getStep().getUrl();
			CloudFormationStackSet cfnStackSet = this.getCfnStackSet();
			JenkinsStackSetOperationPreferences preferences = this.getStep().getOperationPreferences();
			UpdateStackSetRequest req = UpdateStackSetRequest.builder()
				.parameters(parameters)
				.administrationRoleARN(this.getStep().getAdministratorRoleArn())
				.executionRoleName(this.getStep().getExecutionRoleName())
				.operationPreferences(preferences == null ? null : preferences.toStackSetOperationPreferences())
				.tags(tags)
				.build();
			if (this.getStep().batchingOptions != null && this.getStep().batchingOptions.isRegions()) {
				this.getListener().getLogger().println("Batching updates by region");
				List<StackInstanceSummary> summaries = cfnStackSet.findStackSetInstances();
				Map<String, List<StackInstanceSummary>> batches = summaries.stream().collect(Collectors.groupingBy(StackInstanceSummary::region));
				for (Map.Entry<String, List<StackInstanceSummary>> entry : batches.entrySet()) {
					this.getListener().getLogger().format("Updating stack set update batch for region=%s %n", entry.getKey());
					// v1 cloned the request per batch; v2 requests are immutable, so each batch derives
					// its own from the shared builder instead
					UpdateStackSetResponse operation = cfnStackSet.update(this.getStep().readTemplate(this), url, req.toBuilder()
							.regions(entry.getKey())
							.accounts(entry.getValue().stream().map(StackInstanceSummary::account).collect(Collectors.toList()))
							.build()
					);
					cfnStackSet.waitForOperationToComplete(operation.operationId(), getStep().getPollConfiguration().getPollInterval());
					this.getListener().getLogger().format("Updated stack set update batch for region=%s %n", entry.getKey());
				}
			} else {
				UpdateStackSetResponse operation = cfnStackSet.update(this.getStep().readTemplate(this), url, req);
				cfnStackSet.waitForOperationToComplete(operation.operationId(), getStep().getPollConfiguration().getPollInterval());
			}
			return cfnStackSet.describe();
		}


		@Override
		public DescribeStackSetResponse whenStackSetMissing(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception {
			final String url = getStep().getUrl();
			CloudFormationStackSet cfnStack = this.getCfnStackSet();
			cfnStack.create(this.getStep().readTemplate(this), url, parameters, tags,
					this.getStep().getAdministratorRoleArn(), this.getStep().getExecutionRoleName());
			return cfnStack.waitForStackState(StackSetStatus.ACTIVE, getStep().getPollConfiguration().getPollInterval());
		}

		private static final long serialVersionUID = 1L;

	}

}
