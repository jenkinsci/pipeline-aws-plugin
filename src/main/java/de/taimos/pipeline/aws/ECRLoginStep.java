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

package de.taimos.pipeline.aws;

import javax.inject.Inject;

import org.apache.commons.codec.Charsets;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.AuthorizationData;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class ECRLoginStep extends AbstractStepImpl {
	
	private Boolean email = false;
	
	@DataBoundConstructor
	public ECRLoginStep() {
		//
	}
	
	@DataBoundSetter
	public void setEmail(Boolean email) {
		this.email = email;
	}
	
	public Boolean getEmail() {
		return this.email;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "ecrLogin";
		}
		
		@Override
		public String getDisplayName() {
			return "Create and return the ECR login string";
		}
	}
	
	public static class Execution extends AbstractSynchronousStepExecution<String> {
		
		@Inject
		private transient ECRLoginStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		protected String run() throws Exception {
			AmazonECR ecr = AWSClientFactory.create(AmazonECRClientBuilder.standard(), this.envVars);
			
			GetAuthorizationTokenResult token = ecr.getAuthorizationToken(new GetAuthorizationTokenRequest());
			
			if (token.getAuthorizationData().size() != 1) {
				throw new RuntimeException("Did not get authorizationData from AWS");
			}
			
			AuthorizationData authorizationData = token.getAuthorizationData().get(0);
			byte[] bytes = org.apache.commons.codec.binary.Base64.decodeBase64(authorizationData.getAuthorizationToken());
			String data = new String(bytes, Charsets.UTF_8);
			String[] parts = data.split(":");
			if (parts.length != 2) {
				throw new RuntimeException("Got invalid authorizationData from AWS");
			}
			
			String emailString = this.step.getEmail() ? "-e none" : "";
			return String.format("docker login -u %s -p %s %s %s", parts[0], parts[1], emailString, authorizationData.getProxyEndpoint());
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
