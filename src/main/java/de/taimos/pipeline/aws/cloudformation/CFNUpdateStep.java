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

import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
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

public class CFNUpdateStep extends AbstractStepImpl {
	
	private final String stack;
	private String file;
	private String url;
	private String[] params;
	private String[] keepParams;
	private String[] tags;
	private String paramsFile;
	private Integer timeoutInMinutes;
	
	@DataBoundConstructor
	public CFNUpdateStep(String stack) {
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
		return url;
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

	public Integer getTimeoutInMinutes() {
		return this.timeoutInMinutes;
	}

	@DataBoundSetter
	public void setTimeoutInMinutes(Integer timeoutInMinutes) {
		this.timeoutInMinutes = timeoutInMinutes;
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
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
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient CFNUpdateStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String stack = this.step.getStack();
			final String file = this.step.getFile();
			final String url = this.step.getUrl();
			
			final Collection<Parameter> params= this.parseParamsFile(this.step.getParamsFile());
			params.addAll(this.parseParams(this.step.getParams()));
			
			final Collection<Parameter> keepParams = this.parseKeepParams(this.step.getKeepParams());
			final Collection<Tag> tags = this.parseTags(this.step.getTags());
			final Integer timeoutInMinutes = this.step.getTimeoutInMinutes();

			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");
			
			this.listener.getLogger().format("Updating/Creating CloudFormation stack %s %n", stack);
			
			new Thread("cfnUpdate-" + stack) {
				@Override
				public void run() {
					try {
						AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, Execution.this.envVars);
						CloudFormationStack cfnStack = new CloudFormationStack(client, stack, Execution.this.listener);
						if (cfnStack.exists()) {
							ArrayList<Parameter> parameters = new ArrayList<>(params);
							parameters.addAll(keepParams);
							cfnStack.update(Execution.this.readTemplate(file), url, parameters, tags);
						} else {
							cfnStack.create(Execution.this.readTemplate(file), url, params, tags, timeoutInMinutes);
						}
						Execution.this.listener.getLogger().println("Stack update complete");
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
