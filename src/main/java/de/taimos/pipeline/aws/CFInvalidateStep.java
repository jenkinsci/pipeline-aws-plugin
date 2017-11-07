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

import java.util.Arrays;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;
import com.amazonaws.services.cloudfront.model.Paths;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFInvalidateStep extends AbstractStepImpl {
	
	private final String distribution;
	private final String[] paths;
	
	@DataBoundConstructor
	public CFInvalidateStep(String distribution, String[] paths) {
		this.distribution = distribution;
		this.paths = paths.clone();
	}
	
	public String getDistribution() {
		return this.distribution;
	}
	
	public String[] getPaths() {
		return this.paths != null ? this.paths.clone() : null;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "cfInvalidate";
		}
		
		@Override
		public String getDisplayName() {
			return "Invalidate given paths in CloudFront distribution";
		}
	}
	
	public static class Execution extends AbstractSynchronousStepExecution<Void> {
		
		@Inject
		private transient CFInvalidateStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		protected Void run() throws Exception {
			AmazonCloudFront client = AWSClientFactory.create(AmazonCloudFrontClientBuilder.standard(), this.envVars);
			
			String distribution = this.step.getDistribution();
			String[] paths = this.step.getPaths();
			
			this.listener.getLogger().format("Invalidating paths %s in distribution %s %n", Arrays.toString(paths), distribution);
			
			Paths invalidationPaths = new Paths().withItems(paths).withQuantity(paths.length);
			InvalidationBatch batch = new InvalidationBatch(invalidationPaths, Long.toString(System.currentTimeMillis()));
			client.createInvalidation(new CreateInvalidationRequest(distribution, batch));
			
			this.listener.getLogger().println("Invalidation complete");
			return null;
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
