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
import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.cloudformation.parser.JSONParameterFileParser;
import de.taimos.pipeline.aws.cloudformation.parser.ParameterFileParser;
import de.taimos.pipeline.aws.cloudformation.parser.YAMLParameterFileParser;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;

public class CFNCreateChangeSetStep extends AbstractStepImpl {

	private final String changeSet;
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

	@DataBoundConstructor
	public CFNCreateChangeSetStep(String changeSet, String stack) {
		this.changeSet = changeSet;
		this.stack = stack;
	}

	public String getChangeSet() {
		return this.changeSet;
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
		return create;
	}

	@DataBoundSetter
	public void setCreate(Boolean create) {
		this.create = create;
	}

	public String getRoleArn() {
		return roleArn;
	}

	@DataBoundSetter
	public void setRoleArn(String roleArn) {
		this.roleArn = roleArn;
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
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient CFNCreateChangeSetStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String changeSet = this.step.getChangeSet();
			final String stack = this.step.getStack();
			final String file = this.step.getFile();
			final String url = this.step.getUrl();
			final Boolean create = this.step.getCreate();
			final String roleArn = this.step.getRoleArn();

			final Collection<Parameter> params= this.parseParamsFile(this.step.getParamsFile());
			params.addAll(this.parseParams(this.step.getParams()));
			
			final Collection<Parameter> keepParams = this.parseKeepParams(this.step.getKeepParams());
			final Collection<Tag> tags = this.parseTags(this.step.getTags());

			Preconditions.checkArgument(changeSet != null && !changeSet.isEmpty(), "Change Set must not be null or empty");

			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");

			new Thread("cfnCreateChangeSet-" + changeSet) {
				@Override
				public void run() {
					try {
						AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, Execution.this.envVars);
						CloudFormationStack cfnStack = new CloudFormationStack(client, stack, Execution.this.listener);
						if (cfnStack.exists()) {
							Execution.this.listener.getLogger().format("Creating CloudFormation change set %s for existing stack %s %n", changeSet, stack);
							ArrayList<Parameter> parameters = new ArrayList<>(params);
							parameters.addAll(keepParams);
							cfnStack.createChangeSet(changeSet, Execution.this.readTemplate(file), url, parameters, tags, Execution.this.step.getPollInterval(), ChangeSetType.UPDATE, roleArn);
						} else if (create) {
							Execution.this.listener.getLogger().format("Creating CloudFormation change set %s for new stack %s %n", changeSet, stack);
							cfnStack.createChangeSet(changeSet, Execution.this.readTemplate(file), url, params, tags, Execution.this.step.getPollInterval(), ChangeSetType.CREATE, roleArn);
						} else {
							Execution.this.listener.getLogger().println("No stack found with the name and skipped change set creation due to configuration.");
						}
						Execution.this.listener.getLogger().println("Create change set complete");
						Execution.this.getContext().onSuccess(cfnStack.describeOutputs());
					} catch (Exception e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}
		
		private Collection<Parameter> parseParamsFile(String paramsFile) {
			try {
				if (paramsFile == null || paramsFile.isEmpty()) {
					return new ArrayList<>();
				}
				final ParameterFileParser parser;
				if (paramsFile.endsWith(".json")) {
					parser = new JSONParameterFileParser();
				} else
				if (paramsFile.endsWith(".yaml")) {
					parser = new YAMLParameterFileParser();
				} else {
					throw new RuntimeException("Invalid file extension for parameter file (supports json/yaml)");
				}
				return parser.parseParams(this.workspace.child(paramsFile).read());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		private String readTemplate(String file) {
			if (file == null) {
				return null;
			}
			
			FilePath child = this.workspace.child(file);
			try {
				return child.readToString();
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
		
		@Override
		public void stop(@Nonnull Throwable cause) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
