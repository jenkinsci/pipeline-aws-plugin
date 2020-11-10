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

//import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.services.cloudformation.model.DescribeStackSetResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackInstanceSummary;
import com.amazonaws.services.cloudformation.model.StackSetOperationPreferences;
import com.amazonaws.services.cloudformation.model.StackSetStatus;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackSetRequest;
import com.amazonaws.services.cloudformation.model.UpdateStackSetResult;
import com.amazonaws.services.cloudformation.model.CreateStackInstancesResult;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CFNUpdateStackSetForAccountsStep extends AbstractCFNCreateStackSetStep {

	@DataBoundConstructor
	public CFNUpdateStackSetForAccountsStep(String stackSet) {
		super(stackSet);
	}

	private StackSetOperationPreferences operationPreferences;
	private BatchingOptions batchingOptions;
	private Collection<String> accounts;
	private Collection<String> regions;

	public StackSetOperationPreferences getOperationPreferences() {
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

	@DataBoundSetter
	public void setAccounts(Collection<String> accounts) {
		this.accounts = accounts;
	}
	public Collection<String> getAccounts () {return this.accounts;}
	@DataBoundSetter
	public void setRegions(Collection<String> regions) {
		this.regions = regions;
	}
	public Collection<String> getRegions () {return  this.regions; }

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "cfnUpdateStackSetForAccounts";
		}

		@Override
		public String getDisplayName() {
			return "Create or Update CloudFormation StackSet for specific accounts and regions input";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNUpdateStackSetForAccountsStep.Execution(this, context);
	}

	public static class Execution extends AbstractCFNCreateStackSetStep.Execution<CFNUpdateStackSetForAccountsStep> {

		protected Execution(CFNUpdateStackSetForAccountsStep step, @Nonnull StepContext context) {
			super(step, context);
		}

		@Override
		public void checkPreconditions() {
			// Nothing to check here
		}

		@Override
		public String getThreadName() {
			return "cfnUpdateStackSetForAccounts-" + getStep().getStackSet();
		}

		@Override
		public DescribeStackSetResult whenStackSetExists(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception {
			final String url = this.getStep().getUrl();
			CloudFormationStackSet cfnStackSet = this.getCfnStackSet();
			UpdateStackSetRequest req = new UpdateStackSetRequest()
				.withParameters(parameters)
				.withAdministrationRoleARN(this.getStep().getAdministratorRoleArn())
				.withExecutionRoleName(this.getStep().getExecutionRoleName())
				.withOperationPreferences(this.getStep().getOperationPreferences())
				.withTags(tags);
			if (this.getStep().getRegions().isEmpty() || this.getStep().getAccounts().isEmpty()) {
				throw new Exception("Either region list or accounts list are empty. Update stackset by account must supply both ");
			}

			if (this.getStep().batchingOptions != null && this.getStep().batchingOptions.isRegions()) {
				this.getListener().getLogger().println("Batching updates by region");
				List<StackInstanceSummary> summaries = cfnStackSet.findStackSetInstances();
				Map<String, List<StackInstanceSummary>> batches = summaries.stream().collect(Collectors.groupingBy(StackInstanceSummary::getRegion));
				for (Map.Entry<String, List<StackInstanceSummary>> entry : batches.entrySet()) {
					if (this.getStep().getRegions().contains(entry.getKey())) //Naresh
					{
						this.getListener().getLogger().format("Updating stack set update batch for region=%s %n", entry.getKey());
						this.getListener().getLogger().format("Making sure all accounts passed are present in StackSet");
						List<String> accountsInStackSet = entry.getValue().stream().map(StackInstanceSummary::getAccount).collect(Collectors.toList());
						List<String> accountToPass = new ArrayList<String>();
						for (String account:this.getStep().getAccounts())
						{
							if (accountsInStackSet.contains(account)){
								accountToPass.add(account);
							}
						}
						if (!accountToPass.isEmpty()) {
							UpdateStackSetResult operation = cfnStackSet.update(this.getStep().readTemplate(this), url, req.clone()
									.withRegions(entry.getKey())
									.withAccounts(accountToPass)
							);
							cfnStackSet.waitForOperationToComplete(operation.getOperationId(), getStep().getPollConfiguration().getPollInterval());
							this.getListener().getLogger().format("Updated stack set update batch for region=%s %n", entry.getKey());
						}
					}
				}
			} else {
				UpdateStackSetResult operation = cfnStackSet.update(this.getStep().readTemplate(this), url, 	req.clone()
						.withAccounts(this.getStep().getAccounts())
						.withRegions(this.getStep().getRegions()));
				cfnStackSet.waitForOperationToComplete(operation.getOperationId(), this.getStep().getPollConfiguration().getPollInterval());
			}
			return cfnStackSet.describe();
		}


		@Override
		public DescribeStackSetResult whenStackSetMissing(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception {
			final String url = getStep().getUrl();
			CloudFormationStackSet cfnStackSet = this.getCfnStackSet();
			cfnStackSet.create(this.getStep().readTemplate(this), url, parameters, tags,
					this.getStep().getAdministratorRoleArn(), this.getStep().getExecutionRoleName());
			DescribeStackSetResult describeResult = cfnStackSet.waitForStackState(StackSetStatus.ACTIVE, getStep().getPollConfiguration().getPollInterval());
			this.getListener().getLogger().format("Creation of Stackset completed");
			CreateStackInstancesResult stackInstanceCreate = cfnStackSet.createStackInstances(this.getStep().getAccounts(), this.getStep().getRegions());
			String getOpsId = stackInstanceCreate.getOperationId();
			java.time.Duration dur = getStep().getPollConfiguration().getPollInterval();
			cfnStackSet.waitForOperationToComplete(getOpsId,dur);
			return cfnStackSet.describe();
		}

		private static final long serialVersionUID = 1L;

	}

}
