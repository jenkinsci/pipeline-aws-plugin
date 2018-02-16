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

package de.taimos.pipeline.aws.cloudformation.stacksets;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.CreateStackSetRequest;
import com.amazonaws.services.cloudformation.model.CreateStackSetResult;
import com.amazonaws.services.cloudformation.model.DeleteStackSetRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackSetOperationRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackSetOperationResult;
import com.amazonaws.services.cloudformation.model.DescribeStackSetRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackSetResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackSetOperationStatus;
import com.amazonaws.services.cloudformation.model.StackSetStatus;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackSetRequest;
import com.amazonaws.services.cloudformation.model.UpdateStackSetResult;
import hudson.model.TaskListener;

import java.util.Collection;

public class CloudFormationStackSet {

	private final AmazonCloudFormation client;
	private final String stackSet;
	private final TaskListener listener;

	public CloudFormationStackSet(AmazonCloudFormation client, String stackSet, TaskListener listener) {
		this.client = client;
		this.stackSet = stackSet;
		this.listener = listener;
	}

	public boolean exists() {
		try {
			this.client.describeStackSet(new DescribeStackSetRequest().withStackSetName(this.stackSet));
			return true;
		} catch (AmazonCloudFormationException e) {
			if ("StackSetNotFoundException" .equals(e.getErrorCode())) {
				return false;
			} else {
				this.listener.getLogger().format("Got error from describeStacks: %s %n", e.getErrorMessage());
				throw e;
			}
		}
	}

	public CreateStackSetResult create(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags) {
		if ((templateBody == null || templateBody.isEmpty()) && (templateUrl == null || templateUrl.isEmpty())) {
			throw new IllegalArgumentException("Either a file or url for the template must be specified");
		}

		listener.getLogger().println("Creating stack set " + stackSet);
		CreateStackSetRequest req = new CreateStackSetRequest()
				.withStackSetName(this.stackSet)
				.withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM)
				.withTemplateBody(templateBody)
				.withTemplateURL(templateUrl)
				.withParameters(params)
				.withTags(tags);
		CreateStackSetResult result = client.createStackSet(req);
		listener.getLogger().println("Created Stack set stackSetId=" + result.getStackSetId());
		return result;
	}

	public DescribeStackSetResult waitForStackState(StackSetStatus expectedStatus, long pollInterval) throws InterruptedException {
		DescribeStackSetResult result = describe();
		listener.getLogger().println("stackSetId=" + result.getStackSet().getStackSetId() + " status=" + result.getStackSet().getStatus());
		StackSetStatus currentStatus = StackSetStatus.fromValue(result.getStackSet().getStatus());
		if (currentStatus == expectedStatus) {
			listener.getLogger().println("Stack set operation completed successfully");
			return result;
		} else {
			Thread.sleep(pollInterval);
			return waitForStackState(expectedStatus, pollInterval);
		}
	}

	DescribeStackSetOperationResult waitForOperationToComplete(String operationId, long pollInterval) throws InterruptedException {
		listener.getLogger().println("Waiting on operationId=" + operationId);
		DescribeStackSetOperationResult result = describeStackOperation(operationId);
		listener.getLogger().println("operationId=" + operationId + " status=" + result.getStackSetOperation().getStatus());
		switch (StackSetOperationStatus.fromValue(result.getStackSetOperation().getStatus())) {
			case RUNNING:
				Thread.sleep(pollInterval);
				return waitForOperationToComplete(operationId, pollInterval);
			case SUCCEEDED:
				listener.getLogger().println("Stack set operation completed successfully");
				return result;
			case FAILED:
				listener.getLogger().println("Stack set operation completed failed");
				throw new StackSetOperationFailedException(operationId);
			default:
				throw new IllegalStateException("Invalid stack set state=" + result.getStackSetOperation().getStatus());
		}
	}

	public UpdateStackSetResult update(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags) {
		this.listener.getLogger().format("Updating CloudFormation stack set %s %n", this.stackSet);
		UpdateStackSetRequest req = new UpdateStackSetRequest()
				.withStackSetName(this.stackSet)
				.withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM)
				.withParameters(params)
				.withTags(tags);

		if (templateBody != null && !templateBody.isEmpty()) {
			req.setTemplateBody(templateBody);
		} else if (templateUrl != null && !templateUrl.isEmpty()) {
			req.setTemplateURL(templateUrl);
		} else {
			req.setUsePreviousTemplate(true);
		}

		UpdateStackSetResult result = this.client.updateStackSet(req);

		this.listener.getLogger().format("Updated CloudFormation stack set %s %n", this.stackSet);
		return result;
	}

	public void delete() {
		client.deleteStackSet(new DeleteStackSetRequest().withStackSetName(this.stackSet));
	}

	DescribeStackSetResult describe() {
		return this.client.describeStackSet(new DescribeStackSetRequest().withStackSetName(this.stackSet));
	}

	private DescribeStackSetOperationResult describeStackOperation(String operationId) {
		return this.client.describeStackSetOperation(new DescribeStackSetOperationRequest()
				.withStackSetName(stackSet)
				.withOperationId(operationId)
		);
	}
}
