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
 *			http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package de.taimos.pipeline.aws.cloudformation.stacksets;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.Capability;
import software.amazon.awssdk.services.cloudformation.model.CreateStackSetRequest;
import software.amazon.awssdk.services.cloudformation.model.CreateStackSetResponse;
import software.amazon.awssdk.services.cloudformation.model.DeleteStackSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackSetOperationRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackSetOperationResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackSetResponse;
import software.amazon.awssdk.services.cloudformation.model.LimitExceededException;
import software.amazon.awssdk.services.cloudformation.model.ListStackInstancesRequest;
import software.amazon.awssdk.services.cloudformation.model.ListStackInstancesResponse;
import software.amazon.awssdk.services.cloudformation.model.OperationInProgressException;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.StackInstanceSummary;
import software.amazon.awssdk.services.cloudformation.model.StackSetOperationStatus;
import software.amazon.awssdk.services.cloudformation.model.StackSetStatus;
import software.amazon.awssdk.services.cloudformation.model.StaleRequestException;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackSetRequest;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackSetResponse;
import hudson.model.TaskListener;

import de.taimos.pipeline.aws.cloudformation.PollConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CloudFormationStackSet {

	private final CloudFormationClient client;
	private final String stackSet;
	private final TaskListener listener;
	private final SleepStrategy sleepStrategy;

	private static final int MAX_STACK_SET_RETRY_ATTEMPT_COUNT = 10;

	public CloudFormationStackSet(CloudFormationClient client, String stackSet, TaskListener listener, SleepStrategy sleepStrategy) {
		this.client = client;
		this.stackSet = stackSet;
		this.listener = listener;
		this.sleepStrategy = sleepStrategy;
	}

	public boolean exists() {
		try {
			this.client.describeStackSet(DescribeStackSetRequest.builder().stackSetName(this.stackSet).build());
			return true;
		} catch (CloudFormationException e) {
			if ("StackSetNotFoundException".equals(e.awsErrorDetails().errorCode())) {
				return false;
			} else {
				this.listener.getLogger().format("Got error from describeStacks: %s %n", e.awsErrorDetails().errorMessage());
				throw e;
			}
		}
	}

	public CreateStackSetResponse create(String templateBody, String templateUrl, Collection<Parameter> params, Collection<Tag> tags, String administratorRoleArn, String executionRoleName) {
		if ((templateBody == null || templateBody.isEmpty()) && (templateUrl == null || templateUrl.isEmpty())) {
			throw new IllegalArgumentException("Either a file or url for the template must be specified");
		}

		this.listener.getLogger().println("Creating stack set " + this.stackSet);
		CreateStackSetRequest req = CreateStackSetRequest.builder()
			.stackSetName(this.stackSet)
			.capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND)
			.templateBody(templateBody)
			.templateURL(templateUrl)
			.parameters(params)
			.administrationRoleARN(administratorRoleArn)
			.executionRoleName(executionRoleName)
			.tags(tags)
			.build();
		CreateStackSetResponse result = this.client.createStackSet(req);
		this.listener.getLogger().println("Created Stack set stackSetId=" + result.stackSetId());
		return result;
	}

	/**
	 * See PollConfiguration.effectivePollInterval for the substitution this applies.
	 */
	static long pollSleepMillis(Duration pollInterval) {
		return PollConfiguration.effectivePollInterval(pollInterval).toMillis();
	}

	/**
	 * Neither of these waits has a timeout, and a stack-set operation across many accounts runs for
	 * hours, so the poll count is unbounded in practice. They used to recurse once per poll, which
	 * meant a long wait ended in StackOverflowError instead of a result; they are loops now.
	 *
	 * The substitution for a non-positive interval matters because looping removed the crash that used
	 * to end a pollInterval: 0 wait within a second: zero would otherwise mean a tight loop against
	 * DescribeStackSet, and waitForStackState has no throttling retry at all, so the first Throttling
	 * response would fail the build. It shares PollConfiguration.effectivePollInterval with the
	 * CloudFormation waiters so the two cannot drift.
	 *
	 * Overridden by the tests that drive tens of thousands of polls to prove these waits do not
	 * recurse - what those exercise is the stack, not the clock. A seam here rather than a
	 * sub-millisecond interval, so that they cannot be quietly turned into hour-long sleeps by a
	 * later change to how the interval is floored.
	 */
	void sleepBetweenPolls(Duration pollInterval) throws InterruptedException {
		Thread.sleep(pollSleepMillis(pollInterval));
	}

	/**
	 * DELETED is terminal, so waiting for ACTIVE and seeing it means the expected status will never
	 * arrive - and under a loop that would poll forever, holding an executor until the build was
	 * aborted. (The old recursive shape at least ended in StackOverflowError eventually.) An
	 * unmodelled status gets the same treatment, mirroring pollOperationOnce's default and v1's
	 * fromValue, which threw.
	 *
	 * The reverse direction still polls: waiting for DELETED while the set is still ACTIVE is a real
	 * transition, so only DELETED and UNKNOWN_TO_SDK_VERSION are refused here rather than "any status
	 * that is not the expected one".
	 */
	DescribeStackSetResponse waitForStackState(StackSetStatus expectedStatus, Duration pollInterval) throws InterruptedException {
		while (true) {
			DescribeStackSetResponse result = describe();
			this.listener.getLogger().println("stackSetId=" + result.stackSet().stackSetId() + " status=" + result.stackSet().statusAsString());
			StackSetStatus currentStatus = result.stackSet().status();
			if (currentStatus == expectedStatus) {
				this.listener.getLogger().println("Stack set operation completed successfully");
				return result;
			}
			if (currentStatus == StackSetStatus.DELETED || currentStatus == StackSetStatus.UNKNOWN_TO_SDK_VERSION) {
				throw new IllegalStateException("Stack set reached status=" + result.stackSet().statusAsString()
						+ " while waiting for " + expectedStatus + "; it will not reach the expected status");
			}
			this.sleepBetweenPolls(pollInterval);
		}
	}

	DescribeStackSetOperationResponse waitForOperationToComplete(String operationId, Duration pollInterval) throws InterruptedException {
		while (true) {
			DescribeStackSetOperationResponse result = this.pollOperationOnce(operationId);
			if (result != null) {
				return result;
			}
			this.sleepBetweenPolls(pollInterval);
		}
	}

	/**
	 * One poll of the operation: the response once it has finished, or null while it is still running.
	 * Split out so the wait above can be a loop - see waitForStackState for why that matters.
	 */
	private DescribeStackSetOperationResponse pollOperationOnce(String operationId) {
		this.listener.getLogger().println("Waiting on operationId=" + operationId);
		DescribeStackSetOperationResponse result = describeStackOperation(operationId, 0);
		this.listener.getLogger().println("operationId=" + operationId + " status=" + result.stackSetOperation().statusAsString());
		// v1 called fromValue, which threw on an unmodelled status; v2 maps it to
		// UNKNOWN_TO_SDK_VERSION, which falls through to the default branch below
		switch (result.stackSetOperation().status()) {
			case RUNNING:
				return null;
			case SUCCEEDED:
				this.listener.getLogger().println("Stack set operation completed successfully");
				return result;
			case FAILED:
				this.listener.getLogger().println("Stack set operation completed failed");
				throw new StackSetOperationFailedException(operationId);
			default:
				throw new IllegalStateException("Invalid stack set state=" + result.stackSetOperation().statusAsString());
		}
	}

	public UpdateStackSetResponse update(String templateBody, String templateUrl, UpdateStackSetRequest request)  throws InterruptedException {
		this.listener.getLogger().format("Updating CloudFormation stack set %s %n", this.stackSet);
		// v2 request objects are immutable, so the caller's request is augmented through its
		// builder rather than mutated in place
		UpdateStackSetRequest.Builder req = request.toBuilder()
			.stackSetName(this.stackSet)
			.capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND);

		if (templateBody != null && !templateBody.isEmpty()) {
			req.templateBody(templateBody);
		} else if (templateUrl != null && !templateUrl.isEmpty()) {
			req.templateURL(templateUrl);
		} else {
			req.usePreviousTemplate(true);
		}

		return doUpdate(req.build(), 1);
	}

	private UpdateStackSetResponse doUpdate(UpdateStackSetRequest req, int attempt) throws InterruptedException {
		try {
			this.listener.getLogger().format("Attempting to update CloudFormation stack set %s %n", this.stackSet);

			UpdateStackSetResponse result = this.client.updateStackSet(req);
			this.listener.getLogger().format("Updated CloudFormation stack set %s %n", this.stackSet);
			return result;
		} catch (OperationInProgressException | StaleRequestException e) {
			if (attempt == MAX_STACK_SET_RETRY_ATTEMPT_COUNT) {
				this.listener.getLogger().format("Retries exhausted and cloudformation stack set %s is still busy%n", this.stackSet);
				throw e;
			} else {
				long sleepDuration = this.sleepStrategy.calculateSleepDuration(attempt);
				this.listener.getLogger().format("StackSet %s busy. Waiting %d ms %n", this.stackSet, sleepDuration);
				Thread.sleep(sleepDuration);
				return doUpdate(req, attempt + 1);
			}
		} catch (LimitExceededException lee) {
			// awsErrorDetails().errorMessage() is the service message on its own; getMessage()
			// decorates it with service, status code and request id
			if (lee.awsErrorDetails().errorMessage().startsWith("StackSet operations cannot involve more than")) {
				if (attempt == MAX_STACK_SET_RETRY_ATTEMPT_COUNT) {
					this.listener.getLogger().format("Retries exhausted and cloudformation stack set operations %s is still busy%n", this.stackSet);
					throw lee;
				} else {
					long sleepDuration = this.sleepStrategy.calculateSleepDuration(attempt);
					this.listener.getLogger().format("Too many concurrent operations in progress (%s). Waiting for %s update. Waiting %d ms %n", lee.getMessage(), this.stackSet, sleepDuration);
					Thread.sleep(sleepDuration);
					return doUpdate(req, attempt + 1);
				}
			} else {
				throw lee;
			}
		}
	}

	public void delete() {
		this.client.deleteStackSet(DeleteStackSetRequest.builder().stackSetName(this.stackSet).build());
	}

	DescribeStackSetResponse describe() {
		return this.client.describeStackSet(DescribeStackSetRequest.builder().stackSetName(this.stackSet).build());
	}

	public List<StackInstanceSummary> findStackSetInstances() {
		List<StackInstanceSummary> summaries = new ArrayList<>();
		// v1 walked nextToken by hand; the paginator issues the same sequence of calls
		for (ListStackInstancesResponse page : this.client.listStackInstancesPaginator(
				ListStackInstancesRequest.builder().stackSetName(this.stackSet).build())) {
			summaries.addAll(page.summaries());
		}
		return summaries;
	}

	private DescribeStackSetOperationResponse describeStackOperation(String operationId, int attempt) {
		try {
			return this.client.describeStackSetOperation(DescribeStackSetOperationRequest.builder()
					.stackSetName(this.stackSet)
					.operationId(operationId)
					.build()
					);
		} catch (CloudFormationException acfe) {
			if ("Throttling".equals(acfe.awsErrorDetails().errorCode())) {
				this.listener.getLogger().format("Cloudformation throttling exception. RequestId=%s OperationId=%s apiMethod=describeStackOperation", acfe.requestId(), operationId);
				try {
					Thread.sleep(this.sleepStrategy.calculateSleepDuration(attempt));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("describeStackOperation(" + operationId + ") was cancelled");
				}
				return describeStackOperation(operationId, attempt + 1);
			} else {
				throw acfe;
			}
		}
	}
}
