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

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.codedeploy.AmazonCodeDeploy;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClientBuilder;
import com.amazonaws.services.codedeploy.model.GetDeploymentRequest;
import com.amazonaws.services.codedeploy.model.GetDeploymentResult;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

/**
 * @author Giovanni Gargiulo
 */
public class WaitDeployStep extends AbstractStepImpl {
	
	/**
	 * The DeploymentId to monitor. Example: d-3GR0HQLDN
	 */
	private final String deploymentId;
	
	@DataBoundConstructor
	public WaitDeployStep(String deploymentId) {
		this.deploymentId = deploymentId;
	}
	
	public String getDeploymentId() {
		return this.deploymentId;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "awaitDeploymentCompletion";
		}
		
		@Override
		public String getDisplayName() {
			return "Wait for AWS CodeDeploy deployment completion";
		}
		
	}
	
	public static class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
		
		@Inject
		private transient WaitDeployStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		private static final Long POLLING_INTERVAL = 10000L;
		
		private static final String SUCCEEDED_STATUS = "Succeeded";
		
		private static final String FAILED_STATUS = "Failed";
		
		@Override
		protected Void run() throws Exception {
			AmazonCodeDeploy client = AWSClientFactory.create(AmazonCodeDeployClientBuilder.standard(), this.envVars);
			
			String deploymentId = this.step.getDeploymentId();
			this.listener.getLogger().format("Checking Deployment(%s) status", deploymentId);
			
			while (true) {
				GetDeploymentRequest getDeploymentRequest = new GetDeploymentRequest().withDeploymentId(deploymentId);
				GetDeploymentResult deployment = client.getDeployment(getDeploymentRequest);
				String deploymentStatus = deployment.getDeploymentInfo().getStatus();
				
				this.listener.getLogger().format("DeploymentStatus(%s)", deploymentStatus);
				
				if (SUCCEEDED_STATUS.equals(deploymentStatus)) {
					this.listener.getLogger().println("Deployment completed successfully");
					return null;
				} else if (FAILED_STATUS.equals(deploymentStatus)) {
					this.listener.getLogger().println("Deployment completed in error");
					String errorMessage = deployment.getDeploymentInfo().getErrorInformation().getMessage();
					throw new Exception("Deployment Failed: " + errorMessage);
				} else {
					this.listener.getLogger().println("Deployment still in progress... sleeping");
					try {
						Thread.sleep(POLLING_INTERVAL);
					} catch (InterruptedException e) {
						//
					}
				}
				
			}
			
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
