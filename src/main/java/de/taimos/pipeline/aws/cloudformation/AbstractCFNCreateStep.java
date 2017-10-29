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

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.cloudformation.parser.JSONParameterFileParser;
import de.taimos.pipeline.aws.cloudformation.parser.ParameterFileParser;
import de.taimos.pipeline.aws.cloudformation.parser.YAMLParameterFileParser;
import de.taimos.pipeline.aws.utils.IamRoleUtils;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;

abstract class AbstractCFNCreateStep extends AbstractStepImpl {

	private final String stack;
	private String file;
	private String url;
	private String[] params;
	private String[] keepParams;
	private String[] tags;
	private String paramsFile;
	private Long pollInterval = 1000L;
	private Boolean create = true;
	private String roleArn;

	public AbstractCFNCreateStep(String stack) {
		this.stack = stack;
	}
	
	public String getStack() {
		return this.stack;
	}
	
	public String getFile() {
		return this.file;
	}
	
	@DataBoundSetter
	public void setFile(String file) {
		this.file = file;
	}
	
	public String getUrl() {
		return this.url;
	}
	
	@DataBoundSetter
	public void setUrl(String url) {
		this.url = url;
	}
	
	public String[] getParams() {
		return this.params != null ? this.params.clone() : null;
	}
	
	@DataBoundSetter
	public void setParams(String[] params) {
		this.params = params.clone();
	}
	
	public String[] getKeepParams() {
		return this.keepParams != null ? this.keepParams.clone() : null;
	}
	
	@DataBoundSetter
	public void setKeepParams(String[] keepParams) {
		this.keepParams = keepParams.clone();
	}
	
	public String[] getTags() {
		return this.tags != null ? this.tags.clone() : null;
	}
	
	@DataBoundSetter
	public void setTags(String[] tags) {
		this.tags = tags.clone();
	}
	
	public String getParamsFile() {
		return this.paramsFile;
	}
	
	@DataBoundSetter
	public void setParamsFile(String paramsFile) {
		this.paramsFile = paramsFile;
	}

	public Long getPollInterval() {
		return this.pollInterval;
	}
	
	@DataBoundSetter
	public void setPollInterval(Long pollInterval) {
		this.pollInterval = pollInterval;
	}
	
	public Boolean getCreate() {
		return this.create;
	}
	
	@DataBoundSetter
	public void setCreate(Boolean create) {
		this.create = create;
	}
	
	public String getRoleArn() {
		return this.roleArn;
	}
	
	@DataBoundSetter
	public void setRoleArn(String roleArn) {
		this.roleArn = roleArn;
	}

	abstract static class Execution extends AbstractStepExecutionImpl {

		protected abstract AbstractCFNCreateStep getStep();
		protected abstract EnvVars getEnvVars();
		protected abstract FilePath getWorkspace();
		protected abstract TaskListener getListener();

		protected abstract void checkPreconditions();
		protected abstract String getThreadName();
		protected abstract Object whenStackExists(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception;
		protected abstract Object whenStackMissing(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception;

		private String getStack() {
			return this.getStep().getStack();
		}

		private String getParamsFile() {
			return this.getStep().getParamsFile();
		}

		private String[] getParams() {
			return this.getStep().getParams();
		}

		private String[] getKeepParams() {
			return this.getStep().getKeepParams();
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

			final Collection<Parameter> params = this.parseParamsFile(this.getParamsFile());
			params.addAll(this.parseParams(this.getParams()));

			final Collection<Parameter> keepParams = this.parseKeepParams(this.getKeepParams());
			final Collection<Tag> tags = this.parseTags(this.getTags());

			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");
			Preconditions.checkArgument(roleArn == null || IamRoleUtils.validRoleArn(roleArn), "RoleArn must be a valid ARN.");

			this.checkPreconditions();

			new Thread(getThreadName()) {
				@Override
				public void run() {
					try {
						AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, Execution.this.getEnvVars());
						CloudFormationStack cfnStack = new CloudFormationStack(client, stack, Execution.this.getListener());
						if (cfnStack.exists()) {
							ArrayList<Parameter> parameters = new ArrayList<>(params);
							parameters.addAll(keepParams);
							Execution.this.getContext().onSuccess(Execution.this.whenStackExists(parameters, tags));
						} else if (create) {
							Execution.this.getContext().onSuccess(Execution.this.whenStackMissing(params, tags));
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

		private Collection<Parameter> parseParamsFile(String paramsFile) {
			try {
				if (paramsFile == null || paramsFile.isEmpty()) {
					return new ArrayList<>();
				}
				final ParameterFileParser parser;
				if (paramsFile.endsWith(".json")) {
					parser = new JSONParameterFileParser();
				} else if (paramsFile.endsWith(".yaml")) {
					parser = new YAMLParameterFileParser();
				} else {
					throw new RuntimeException("Invalid file extension for parameter file (supports json/yaml)");
				}
				return parser.parseParams(this.getWorkspace().child(paramsFile).read());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		private Collection<Tag> parseTags(String[] tags) {
			Collection<Tag> tagList = new ArrayList<>();
			if (tags == null) {
				return tagList;
			}
			for (String tag : tags) {
				int i = tag.indexOf("=");
				if (i < 0) {
					throw new RuntimeException("Missing = in tag " + tag);
				}
				String key = tag.substring(0, i);
				String value = tag.substring(i + 1);
				tagList.add(new Tag().withKey(key).withValue(value));
			}
			return tagList;
		}

		private Collection<Parameter> parseParams(String[] params) {
			Collection<Parameter> parameters = new ArrayList<>();
			if (params == null) {
				return parameters;
			}
			for (String param : params) {
				int i = param.indexOf("=");
				if (i < 0) {
					throw new RuntimeException("Missing = in param " + param);
				}
				String key = param.substring(0, i);
				String value = param.substring(i + 1);
				parameters.add(new Parameter().withParameterKey(key).withParameterValue(value));
			}
			return parameters;
		}

		private Collection<Parameter> parseKeepParams(String[] params) {
			Collection<Parameter> parameters = new ArrayList<>();
			if (params == null) {
				return parameters;
			}
			for (String param : params) {
				parameters.add(new Parameter().withParameterKey(param).withUsePreviousValue(true));
			}
			return parameters;
		}

		protected CloudFormationStack getCfnStack() {
			AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, this.getEnvVars());
			return new CloudFormationStack(client, this.getStack(), this.getListener());
		}

		protected String readTemplate(String file) {
			if (file == null) {
				return null;
			}

			FilePath child = this.getWorkspace().child(file);
			try {
				return child.readToString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

}
