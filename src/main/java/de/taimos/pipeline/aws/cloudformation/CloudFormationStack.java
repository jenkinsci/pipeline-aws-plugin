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

package de.taimos.pipeline.aws.cloudformation;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.cloudformation.CloudFormationAsyncClient;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.Capability;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetStatus;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetType;
import software.amazon.awssdk.services.cloudformation.model.CreateChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.CreateStackResponse;
import software.amazon.awssdk.services.cloudformation.model.DeleteChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DeleteStackRequest;
import software.amazon.awssdk.services.cloudformation.model.DeleteStackResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cloudformation.model.ExecuteChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.OnFailure;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.RollbackConfiguration;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackResponse;
import hudson.model.TaskListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class CloudFormationStack {

	private static final String UPDATE_STATUS_OUTPUT = "jenkinsStackUpdateStatus";

	private final CloudFormationClient client;
	private final CloudFormationAsyncClient asyncClient;

	private final String stack;
	private final TaskListener listener;

	public CloudFormationStack(CloudFormationClient client, CloudFormationAsyncClient asyncClient, String stack, TaskListener listener) {
		if (listener == null) {
			throw new IllegalStateException("listener is null");
		}
		this.client = client;
		this.asyncClient = asyncClient;
		this.stack = stack;
		this.listener = listener;
	}

	public boolean exists() {
		try {
			DescribeStacksResponse result = this.client.describeStacks(DescribeStacksRequest.builder().stackName(this.stack).build());
			if (this.listener.getLogger() == null) {
				throw new IllegalStateException("logger is null");
			}
			this.listener.getLogger().format("Found %d stacks in result %n", result.stacks().size());
			for (Stack stack : result.stacks()) {
				this.listener.getLogger().format("Found stackName=%s stackId=%s status=%s statusReason=%s in result %n", stack.stackName(), stack.stackId(), stack.stackStatus(), stack.stackStatusReason());
			}
			return !result.stacks().isEmpty();
		} catch (CloudFormationException e) {
			AwsErrorDetails details = e.awsErrorDetails();
			this.listener.getLogger().format("Got error from describeStacks: %s %n", details.errorMessage());
			if ("AccessDenied".equals(details.errorCode())) {
				throw e;
			} else if ("ValidationError".equals(details.errorCode()) && details.errorMessage().contains("does not exist")) {
				return false;
			} else {
				throw e;
			}
		}
	}

	public boolean changeSetExists(String changeSetName) {
		try {
			DescribeChangeSetResponse result = this.client.describeChangeSet(DescribeChangeSetRequest.builder().stackName(this.stack).changeSetName(changeSetName).build());
			this.listener.getLogger().format("Found changeSet=%s status=%s statusReason=%s %n", result.changeSetName(), result.status(), result.statusReason());
			return true;
		} catch (CloudFormationException e) {
			AwsErrorDetails details = e.awsErrorDetails();
			this.listener.getLogger().format("Got error from describeStacks: %s %n", details.errorMessage());
			if ("AccessDenied".equals(details.errorCode())) {
				throw e;
			}
			return false;
		}
	}

	private boolean emptyChangeSet(String changeSetName) {
		DescribeChangeSetResponse result = this.client.describeChangeSet(DescribeChangeSetRequest.builder().stackName(this.stack).changeSetName(changeSetName).build());
		return ChangeSetStatus.FAILED == result.status() &&
				(result.statusReason().toLowerCase().contains("the submitted information didn't contain changes") ||
				result.statusReason().toLowerCase().contains("no updates are to be performed"));
	}

	public Map<String, String> describeOutputs() {
		DescribeStacksResponse result = this.client.describeStacks(DescribeStacksRequest.builder().stackName(this.stack).build());
		Stack cfnStack = result.stacks().get(0);
		Map<String, String> map = new HashMap<>();
		for (Output output : cfnStack.outputs()) {
			map.put(output.outputKey(), output.outputValue());
		}
		return map;
	}

	public Map<String, String> create(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, Collection<String> notificationARNs, PollConfiguration pollConfiguration, String roleArn, String onFailure, Boolean enableTerminationProtection) throws ExecutionException {
		if ((templateBody == null || templateBody.isEmpty()) && (templateUrl == null || templateUrl.isEmpty())) {
			throw new IllegalArgumentException("Either a file or url for the template must be specified");
		}

		CreateStackRequest.Builder req = CreateStackRequest.builder();
		req.stackName(this.stack).capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).enableTerminationProtection(enableTerminationProtection);
		req.templateBody(templateBody).templateURL(templateUrl).parameters(params).tags(tags).notificationARNs(notificationARNs)
				.timeoutInMinutes(pollConfiguration.getTimeout() == null ? null : (int) pollConfiguration.getTimeout().toMinutes())
				.roleARN(roleArn)
				.onFailure(OnFailure.valueOf(onFailure));
		this.asyncClient.createStack(req.build());

		getEventPrinter().waitAndPrintStackEvents(this.stack, this.asyncClient.waiter()::waitUntilStackCreateComplete, pollConfiguration);

		Map<String, String> outputs = this.describeOutputs();
		outputs.put(UPDATE_STATUS_OUTPUT, "true");
		return outputs;
	}

	protected EventPrinter getEventPrinter() {
		return new EventPrinter(this.client, this.listener);
	}


	public Map<String, String> update(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, Collection<String> notificationARNs, PollConfiguration pollConfiguration, String roleArn, RollbackConfiguration rollbackConfig) throws ExecutionException {
		try {
			UpdateStackRequest.Builder req = UpdateStackRequest.builder();
			req.stackName(this.stack).capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND);

			if (templateBody != null && !templateBody.isEmpty()) {
				req.templateBody(templateBody);
			} else if (templateUrl != null && !templateUrl.isEmpty()) {
				req.templateURL(templateUrl);
			} else {
				req.usePreviousTemplate(true);
			}

			req.rollbackConfiguration(rollbackConfig);

			req.parameters(params);
			if(tags != null && tags.size() > 0){
				req.tags(tags);
			}
			if (notificationARNs != null && notificationARNs.size() > 0) {
				req.notificationARNs(notificationARNs);
			}
			req.roleARN(roleArn);

			CompletableFuture<UpdateStackResponse> update = this.asyncClient.updateStack(req.build());

			getEventPrinter().waitAndPrintStackEvents(this.stack, this.asyncClient.waiter()::waitUntilStackUpdateComplete, pollConfiguration);

			this.listener.getLogger().format("Updated CloudFormation stack %s %n", this.stack);

			Map<String, String> outputs = this.describeOutputs();
			outputs.put(UPDATE_STATUS_OUTPUT, "true");
			return outputs;
		} catch (CloudFormationException e) {
			if (e.getMessage().contains("No updates are to be performed")) {
				this.listener.getLogger().format("No updates were needed for CloudFormation stack %s %n", this.stack);
				Map<String, String> outputs = this.describeOutputs();
				outputs.put(UPDATE_STATUS_OUTPUT, "false");
				return outputs;
			}
			this.listener.getLogger().format("Failed to update CloudFormation stack %s %n", this.stack);
			throw e;
		}
	}

	public void createChangeSet(String changeSetName, String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, Collection<String> notificationARNs, PollConfiguration pollConfiguration, ChangeSetType changeSetType, String roleArn, RollbackConfiguration rollbackConfig) throws ExecutionException {
		ChangeSetType effectiveChangeSetType;
		if (isInReview()) {
			effectiveChangeSetType = ChangeSetType.CREATE;
		} else {
			effectiveChangeSetType = changeSetType;
		}
		doCreateChangeSet(changeSetName, templateBody, templateUrl, params, tags, notificationARNs, pollConfiguration, effectiveChangeSetType, roleArn, rollbackConfig);
	}

	private void doCreateChangeSet(String changeSetName, String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, Collection<String> notificationARNs, PollConfiguration pollConfiguration, ChangeSetType changeSetType, String roleArn, RollbackConfiguration rollbackConfig) throws ExecutionException {
		try {
			CreateChangeSetRequest.Builder req = CreateChangeSetRequest.builder();
			req.changeSetName(changeSetName).stackName(this.stack).capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).changeSetType(changeSetType);

			if (ChangeSetType.CREATE.equals(changeSetType)) {
				this.listener.getLogger().format("Creating CloudFormation change set %s for new stack %s %n", changeSetName, this.stack);
				if ((templateBody == null || templateBody.isEmpty()) && (templateUrl == null || templateUrl.isEmpty())) {
					throw new IllegalArgumentException("Either a file or url for the template must be specified");
				}
				req.templateBody(templateBody).templateURL(templateUrl);
			} else if (ChangeSetType.UPDATE.equals(changeSetType)) {
				this.listener.getLogger().format("Creating CloudFormation change set %s for existing stack %s %n", changeSetName, this.stack);
				if (templateBody != null && !templateBody.isEmpty()) {
					req.templateBody(templateBody);
				} else if (templateUrl != null && !templateUrl.isEmpty()) {
					req.templateURL(templateUrl);
				} else {
					req.usePreviousTemplate(true);
				}
			} else {
				throw new IllegalArgumentException("Cannot create a CloudFormation change set without a valid change set type.");
			}

			req.parameters(params).tags(tags).notificationARNs(notificationARNs).roleARN(roleArn).rollbackConfiguration(rollbackConfig);

			this.asyncClient.createChangeSet(req.build());

			getEventPrinter().waitAndPrintChangeSetEvents(this.stack, changeSetName, this.asyncClient.waiter(), pollConfiguration);

			this.listener.getLogger().format("Created CloudFormation change set %s for stack %s %n", changeSetName, this.stack);

		} catch (ExecutionException e) {
			try {
				if (this.changeSetExists(changeSetName) && this.emptyChangeSet(changeSetName)) {
					// Ignore the failed creation of a change set with no changes.
					this.listener.getLogger().format("Created empty change set %s for stack %s %n", changeSetName, this.stack);
					return;
				}
			} catch (Throwable throwable) {
				e.addSuppressed(throwable);
			}
			this.listener.getLogger().format("Failed to create CloudFormation change set %s for stack %s %n", changeSetName, this.stack);
			throw e;
		}
	}

	public Map<String, String> executeChangeSet(String changeSetName, PollConfiguration pollConfiguration) throws ExecutionException {
		if (!this.exists() || this.emptyChangeSet(changeSetName)) {
			// If the change set has no changes or the stack was not prepared we should simply delete it.
			this.listener.getLogger().format("Deleting empty change set %s for stack %s %n", changeSetName, this.stack);
			DeleteChangeSetRequest req = DeleteChangeSetRequest.builder().changeSetName(changeSetName).stackName(this.stack).build();
			this.client.deleteChangeSet(req);

			Map<String, String> outputs = this.describeOutputs();
			outputs.put(UPDATE_STATUS_OUTPUT, "false");
			return outputs;
		} else {
			this.listener.getLogger().format("Executing change set %s for stack %s %n", changeSetName, this.stack);
			ExecuteChangeSetRequest req = ExecuteChangeSetRequest.builder().changeSetName(changeSetName).stackName(this.stack).build();

			final Function<DescribeStacksRequest, CompletableFuture<WaiterResponse<DescribeStacksResponse>>> waiter;
			if (this.isInReview()) {
				waiter = this.asyncClient.waiter()::waitUntilStackCreateComplete;
			} else {
				waiter = this.asyncClient.waiter()::waitUntilStackUpdateComplete;
			}

			this.asyncClient.executeChangeSet(req);
			getEventPrinter().waitAndPrintStackEvents(this.stack, waiter, pollConfiguration);
			this.listener.getLogger().format("Executed change set %s for stack %s %n", changeSetName, this.stack);

			Map<String, String> outputs = this.describeOutputs();
			outputs.put(UPDATE_STATUS_OUTPUT, "true");
			return outputs;
		}
	}

	public void delete(PollConfiguration pollConfiguration, String[] retainResources, String roleArn, String clientRequestToken) throws ExecutionException {
		DeleteStackRequest.Builder req = DeleteStackRequest.builder().stackName(this.stack).roleARN(roleArn).clientRequestToken(clientRequestToken);
		if (retainResources != null){
			req.retainResources(retainResources);
		}
		this.asyncClient.deleteStack(req.build());
		getEventPrinter().waitAndPrintStackEvents(this.stack, this.asyncClient.waiter()::waitUntilStackDeleteComplete, pollConfiguration);
	}

	public DescribeChangeSetResponse describeChangeSet(String changeSet) {
		return this.client.describeChangeSet(DescribeChangeSetRequest.builder()
				.stackName(this.stack)
				.changeSetName(changeSet).build()
		);
	}

	private boolean isInReview() {
		if (this.exists()) {
			DescribeStacksResponse result = this.client.describeStacks(DescribeStacksRequest.builder().stackName(this.stack).build());
			return result.stacks().size() > 0 && result.stacks().get(0).stackStatus().equals("REVIEW_IN_PROGRESS");
		}
		return false;
	}
}
