/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.model.CreateRepositoryRequest;
import com.google.common.base.Preconditions;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class ECRCreateRepositoryStep extends AbstractStepImpl {

	private final String repoName;
	private final String regionName;

	@DataBoundConstructor
	public ECRCreateRepositoryStep(String repoName, String regionName) {
		this.repoName = repoName;
		this.regionName = regionName;
	}
	
	public String getRepoName() {
		return this.repoName;
	}

	public String getRegionName() {
		return this.regionName;
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "ecrCreateRepository";
		}
		
		@Override
		public String getDisplayName() {
			return "Creates an ECR Repository";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient ECRCreateRepositoryStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String repoName = this.step.getRepoName();
			final String regionName = this.step.getRegionName();

			Preconditions.checkArgument(repoName != null && !repoName.isEmpty(), "Repo Name must not be null or empty");
			
			new Thread("ecrCreateRepository") {
				@Override
				public void run() {
					try {
						Execution.this.listener.getLogger().format("Creating ECR repository %s", repoName);
						AmazonECRClient ecrClient = AWSClientFactory.create(AmazonECRClient.class, envVars);
						if(null != regionName && !regionName.isEmpty()) {
							ecrClient.setRegion(Region.getRegion(Regions.fromName(regionName)));
						}
						CreateRepositoryRequest createRepositoryRequest = new CreateRepositoryRequest();
						createRepositoryRequest.setRepositoryName(repoName);
						ecrClient.createRepository(createRepositoryRequest);
						Execution.this.listener.getLogger().println(" Repository Created");
						Execution.this.getContext().onSuccess(null);
					} catch (Exception e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}
		
		@Override
		public void stop(@Nonnull Throwable cause) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}
}
