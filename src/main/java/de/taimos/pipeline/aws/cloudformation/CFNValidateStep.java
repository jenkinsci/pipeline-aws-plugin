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

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.ValidateTemplateRequest;

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class CFNValidateStep extends AbstractStepImpl {
	
	private String file;
	private String url;
	
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
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "cfnValidate";
		}
		
		@Override
		public String getDisplayName() {
			return "Validate CloudFormation template";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient CFNValidateStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		@StepContextParameter
		private transient FilePath workspace;
		
		@Override
		public boolean start() throws Exception {
			final String file = this.step.getFile();
			final String url = this.step.getUrl();
			
			if ((file == null || file.isEmpty()) && (url == null || url.isEmpty())) {
				throw new IllegalArgumentException("Either a file or url for the template must be specified");
			}
			
			this.listener.getLogger().format("Validating CloudFormation template %s %n", file);
			
			final String template = this.readTemplate(file);
			
			new Thread("cfnValidate-" + file) {
				@Override
				public void run() {
					AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, Execution.this.envVars);
					try {
						ValidateTemplateRequest request = new ValidateTemplateRequest();
						if (template != null) {
							request.withTemplateBody(template);
						} else {
							request.withTemplateURL(url);
						}
						client.validateTemplate(request);
						Execution.this.getContext().onSuccess(null);
					} catch (AmazonCloudFormationException e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}
		
		private String readTemplate(String file) {
			if (file == null || file.isEmpty()) {
				return null;
			}
			FilePath child = this.workspace.child(file);
			try {
				return child.readToString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public void stop(@Nonnull Throwable cause) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
