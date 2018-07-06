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


import java.io.IOException;
import java.util.Collection;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.OnFailure;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.cloudformation.TemplateStepBase;
import de.taimos.pipeline.aws.cloudformation.parser.ParameterParser;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;

abstract class AbstractCFNCreateStackSetStep extends TemplateStepBase {

	private final String stackSet;
	private String administratorRoleArn;
	private String executionRoleName;
	private String onFailure = OnFailure.DELETE.toString();

	public AbstractCFNCreateStackSetStep(String stackSet) {
		this.stackSet = stackSet;
	}

	public String getStackSet() {
		return this.stackSet;
	}

	public String getOnFailure() {
		return this.onFailure;
	}

	@DataBoundSetter
	public void setOnFailure(String onFailure) {
		this.onFailure = onFailure;
	}

	@DataBoundSetter
	public void setAdministratorRoleArn(String administratorRoleArn) {
		this.administratorRoleArn = administratorRoleArn;
	}

	public String getAdministratorRoleArn() {
		return administratorRoleArn;
	}

	public String getExecutionRoleName() {
		return executionRoleName;
	}

	@DataBoundSetter
	public void setExecutionRoleName(String executionRoleName) {
		this.executionRoleName = executionRoleName;
	}

	abstract static class Execution<C extends AbstractCFNCreateStackSetStep> extends StepExecution {

		private final transient C step;

		protected abstract void checkPreconditions();

		protected abstract String getThreadName();

		protected abstract Object whenStackSetExists(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception;

		protected abstract Object whenStackSetMissing(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception;

		protected Execution(C step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		private String getStackSet() {
			return this.getStep().getStackSet();
		}

		private Boolean getCreate() {
			return this.getStep().getCreate();
		}

		@Override
		public boolean start() throws Exception {

			final String stackSet = this.getStackSet();
			final Boolean create = this.getCreate();

			Preconditions.checkArgument(stackSet != null && !stackSet.isEmpty(), "Stack set must not be null or empty");

			this.checkPreconditions();

			new Thread(Execution.this.getThreadName()) {
				@Override
				public void run() {
					try {
						AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.getEnvVars());
						CloudFormationStackSet cfnStackSet = new CloudFormationStackSet(client, stackSet, Execution.this.getListener(), SleepStrategy.EXPONENTIAL_BACKOFF_STRATEGY);
						if (cfnStackSet.exists()) {
							Collection<Parameter> parameters = ParameterParser.parseWithKeepParams(getWorkspace(), getStep());
							Execution.this.getContext().onSuccess(Execution.this.whenStackSetExists(parameters, getStep().getAwsTags(Execution.this)));
						} else if (create) {
							Collection<Parameter> parameters = ParameterParser.parse(getWorkspace(), getStep());
							Execution.this.getContext().onSuccess(Execution.this.whenStackSetMissing(parameters, getStep().getAwsTags(Execution.this)));
						} else {
							Execution.this.getListener().getLogger().println("No stack set found with the name=" + stackSet + " and skipped creation due to configuration.");
							Execution.this.getContext().onSuccess(null);
						}
					} catch (Exception e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}

		@Override
		public void stop(@Nonnull Throwable throwable) throws Exception {
		}

		protected CloudFormationStackSet getCfnStackSet() {
			AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), this.getEnvVars());
			return new CloudFormationStackSet(client, this.getStackSet(), this.getListener(), SleepStrategy.EXPONENTIAL_BACKOFF_STRATEGY);
		}

		public C getStep() {
			return this.step;
		}

		public TaskListener getListener() {
			try {
				return this.getContext().get(TaskListener.class);
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		public EnvVars getEnvVars() {
			try {
				return this.getContext().get(EnvVars.class);
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		public FilePath getWorkspace() {
			try {
				return this.getContext().get(FilePath.class);
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

}
