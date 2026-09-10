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
import software.amazon.awssdk.services.cloudformation.model.DescribeStackSetResponse;
import software.amazon.awssdk.services.cloudformation.model.OnFailure;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import de.taimos.pipeline.aws.AwsSdkResponseToJson;
import de.taimos.pipeline.aws.cloudformation.TemplateStepBase;
import de.taimos.pipeline.aws.cloudformation.parser.ParameterParser;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

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

	abstract static class Execution<C extends AbstractCFNCreateStackSetStep> extends SynchronousNonBlockingStepExecution<Map<String, Object>> {

		private final transient C step;

		protected abstract void checkPreconditions();

		protected abstract String getThreadName();

		protected abstract DescribeStackSetResponse whenStackSetExists(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception;

		protected abstract DescribeStackSetResponse whenStackSetMissing(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception;

		protected Execution(C step, @NonNull StepContext context) {
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
		public Map<String, Object> run() throws Exception {

			final String stackSet = this.getStackSet();
			final Boolean create = this.getCreate();

			Preconditions.checkArgument(stackSet != null && !stackSet.isEmpty(), "Stack set must not be null or empty");

			this.checkPreconditions();

			CloudFormationClient client = AWSClientFactory.create(CloudFormationClient.builder(), Execution.this.getContext(), Execution.this.getEnvVars());
			CloudFormationStackSet cfnStackSet = AWSUtilFactory.newCFStackSet(client, stackSet, Execution.this.getListener(), SleepStrategy.EXPONENTIAL_BACKOFF_STRATEGY);
			// Converted to a map on the way out: the v2 response types are not Serializable, so
			// handing one to the pipeline fails with NotSerializableException as soon as the value
			// outlives a step boundary, and their fields are unreadable from a sandboxed script
			// anyway. This matches what the ECR steps return.
			if (cfnStackSet.exists()) {
				Collection<Parameter> parameters = ParameterParser.parseWithKeepParams(getWorkspace(), getStep());
				return toMap(Execution.this.whenStackSetExists(parameters, getStep().getAwsTags(Execution.this)));
			} else if (create) {
				Collection<Parameter> parameters = ParameterParser.parse(getWorkspace(), getStep());
				return toMap(Execution.this.whenStackSetMissing(parameters, getStep().getAwsTags(Execution.this)));
			} else {
				Execution.this.getListener().getLogger().println("No stack set found with the name=" + stackSet + " and skipped creation due to configuration.");
				return null;
			}
		}

		private static Map<String, Object> toMap(DescribeStackSetResponse response) {
			return response == null ? null : AwsSdkResponseToJson.convertToMap(response);
		}

		protected CloudFormationStackSet getCfnStackSet() {
			CloudFormationClient client = AWSClientFactory.create(CloudFormationClient.builder(), this.getContext(), this.getEnvVars());
			return AWSUtilFactory.newCFStackSet(client, this.getStackSet(), this.getListener(), SleepStrategy.EXPONENTIAL_BACKOFF_STRATEGY);
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
