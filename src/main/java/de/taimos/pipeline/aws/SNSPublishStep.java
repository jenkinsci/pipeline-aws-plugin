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
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishResult;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class SNSPublishStep extends AbstractStepImpl {
	
	private final String topicArn;
	private final String subject;
	private final String message;
	private final String regionName;
	
	@DataBoundConstructor
	public SNSPublishStep(String topicArn, String subject, String message, String regionName) {
		this.topicArn = topicArn;
		this.subject = subject;
		this.message = message;
		this.regionName = regionName;
	}
	
	public String getTopicArn() {
		return this.topicArn;
	}
	
	public String getSubject() {
		return this.subject;
	}
	
	public String getMessage() {
		return this.message;
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
			return "snsPublish";
		}
		
		@Override
		public String getDisplayName() {
			return "Publish notification to SNS";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient SNSPublishStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String topicArn = this.step.getTopicArn();
			final String subject = this.step.getSubject();
			final String message = this.step.getMessage();
			final String regionName = this.step.getRegionName();
			
			new Thread("snsPublish") {
				@Override
				public void run() {
					try {
						AmazonSNSClient snsClient = AWSClientFactory.create(AmazonSNSClient.class, Execution.this.envVars);
						if(null != regionName && !regionName.isEmpty()) {
							snsClient.setRegion(Region.getRegion(Regions.fromName(regionName)));
						}
						
						Execution.this.listener.getLogger().format("Publishing notification %s to %s %n", subject, topicArn);
						PublishResult result = snsClient.publish(topicArn, message, subject);
						Execution.this.listener.getLogger().format("Message published as %s %n", result.getMessageId());
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
