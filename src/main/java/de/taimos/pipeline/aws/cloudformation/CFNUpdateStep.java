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

import java.util.Collection;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class CFNUpdateStep extends AbstractCFNCreateStep {
	
	private Integer timeoutInMinutes;
	
	@DataBoundConstructor
	public CFNUpdateStep(String stack) {
		super(stack);
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
	
	public static class Execution extends AbstractCFNCreateStep.Execution {
		
		@Inject
		private transient CFNUpdateStep step;
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
		}
		
		@Override
		public String getThreadName() {
			return "cfnUpdate-" + this.step.getStack();
		}
		
		@Override
		public Object whenStackExists(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception {
			final String file = this.getStep().getFile();
			final String url = this.getStep().getUrl();
			CloudFormationStack cfnStack = this.getCfnStack();
			cfnStack.update(this.readTemplate(file), url, parameters, tags, this.getStep().getPollInterval(), this.getStep().getRoleArn());
			return cfnStack.describeOutputs();
		}
		
		@Override
		public Object whenStackMissing(Collection<Parameter> parameters, Collection<Tag> tags) throws Exception {
			final String file = this.getStep().getFile();
			final String url = this.getStep().getUrl();
			CloudFormationStack cfnStack = this.getCfnStack();
			cfnStack.create(this.readTemplate(file), url, parameters, tags, this.step.getTimeoutInMinutes(), this.getStep().getPollInterval(), this.getStep().getRoleArn());
			return cfnStack.describeOutputs();
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
