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
import com.amazonaws.services.cloudformation.model.RollbackConfiguration;
import com.amazonaws.services.cloudformation.model.Tag;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.cloudformation.parser.ParameterParser;
import de.taimos.pipeline.aws.utils.IamRoleUtils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;

abstract class AbstractCFNCreateStep extends TemplateStepBase {

	private final String stack;
	private String roleArn;
	private String onFailure = OnFailure.DELETE.toString();

	public AbstractCFNCreateStep(String stack) {
		this.stack = stack;
	}

	public String getStack() {
		return this.stack;
	}

	public String getRoleArn() {
		return this.roleArn;
	}

	@DataBoundSetter
	public void setRoleArn(String roleArn) {
		this.roleArn = roleArn;
	}

	public String getOnFailure() {
		return this.onFailure;
	}

	@DataBoundSetter
	public void setOnFailure(String onFailure) {
		this.onFailure = onFailure;
	}

	abstract static class Execution<C extends AbstractCFNCreateStep> extends StepExecution {

		private final transient C step;

		protected abstract void checkPreconditions();

		protected abstract String getThreadName();

		protected abstract Object whenStackExists(Collection<Parameter> parameters, Collection<Tag> tags, RollbackConfiguration rollbackConfiguration) throws Exception;

		protected abstract Object whenStackMissing(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception;

		protected Execution(C step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		private String getStack() {
			return this.getStep().getStack();
		}

		private String[] getTags() {
			return this.getStep().getTags();
		}

		private String getRoleArn() {
			return this.getStep().getRoleArn();
		}

		private Boolean getCreate() {
			return this.getStep().getCreate();
		}

		@Override
		public boolean start() throws Exception {

			final String stack = this.getStack();
			final String roleArn = this.getRoleArn();
			final Boolean create = this.getCreate();


			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");
			Preconditions.checkArgument(roleArn == null || IamRoleUtils.validRoleArn(roleArn), "RoleArn must be a valid ARN.");

			this.checkPreconditions();

			new Thread(Execution.this.getThreadName()) {
				@Override
				public void run() {
					try {
						AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.getEnvVars());
						CloudFormationStack cfnStack = new CloudFormationStack(client, stack, Execution.this.getListener());
						if (cfnStack.exists()) {
							Collection<Parameter> parameters = ParameterParser.parseWithKeepParams(Execution.this.getWorkspace(), Execution.this.getStep());
							Execution.this.getContext().onSuccess(Execution.this.whenStackExists(parameters, Execution.this.getStep().getAwsTags(Execution.this), Execution.this.getStep().getRollbackConfiguration()));
						} else if (create) {
							Collection<Parameter> parameters = ParameterParser.parse(Execution.this.getWorkspace(), Execution.this.getStep());
							Execution.this.getContext().onSuccess(Execution.this.whenStackMissing(parameters, Execution.this.getStep().getAwsTags(Execution.this)));
						} else {
							Execution.this.getListener().getLogger().println("No stack found with the name and skipped creation due to configuration.");
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

		protected CloudFormationStack getCfnStack() {
			AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), this.getEnvVars());
			return new CloudFormationStack(client, this.getStack(), this.getListener());
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
