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

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishResult;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;
import org.kohsuke.stapler.DataBoundSetter;

public class SNSPublishStep extends Step {

	private static final String STRING_DATATYPE = "String";

	private final String topicArn;
	private final String subject;
	private final String message;

	private Map<String, String> messageAttributes;

	@DataBoundConstructor
	public SNSPublishStep(String topicArn, String subject, String message) {
		this.topicArn = topicArn;
		this.subject = subject;
		this.message = message;
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

	@DataBoundSetter
	public void setMessageAttributes(Map<String, String> messageAttributes) {
		this.messageAttributes = new HashMap<>(messageAttributes);
	}

	public Map<String, String> getMessageAttributes() {
		return messageAttributes;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new SNSPublishStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
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

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

		private final transient SNSPublishStep step;

		public Execution(SNSPublishStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			final String topicArn = this.step.getTopicArn();
			final String subject = this.step.getSubject();
			final String message = this.step.getMessage();
			final Map<String, String> messageAttributes = this.step.getMessageAttributes();

			TaskListener listener = this.getContext().get(TaskListener.class);
			AmazonSNS snsClient = AWSClientFactory.create(AmazonSNSClientBuilder.standard(), this.getContext());

			listener.getLogger().format("Publishing notification %s to %s %n", subject, topicArn);

			PublishRequest publishRequest = new PublishRequest()
					.withTopicArn(topicArn).withMessage(message).withSubject(subject);

			if (messageAttributes != null && !messageAttributes.isEmpty()) {
				for (Map.Entry<String, String> entry : messageAttributes.entrySet()) {
					MessageAttributeValue value = new MessageAttributeValue();
					value.setStringValue(entry.getValue());
					value.setDataType(STRING_DATATYPE);
					publishRequest.addMessageAttributesEntry(entry.getKey(), value);
				}
			}

			PublishResult result = snsClient.publish(publishRequest);

			listener.getLogger().format("Message published as %s %n", result.getMessageId());
			return null;
		}

		@Serial
		private static final long serialVersionUID = 1L;

	}

}
