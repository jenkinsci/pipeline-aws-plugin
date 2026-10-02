package de.taimos.pipeline.aws.cloudformation;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.retry.backoff.FixedDelayBackoffStrategy;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsRequest;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.Capability;
import software.amazon.awssdk.services.cloudformation.model.Change;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetStatus;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetType;
import software.amazon.awssdk.services.cloudformation.model.CreateChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.DeleteStackRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cloudformation.model.ExecuteChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.OnFailure;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.RollbackConfiguration;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackRequest;
import software.amazon.awssdk.services.cloudformation.waiters.CloudFormationWaiter;
import hudson.model.TaskListener;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CloudformationStackTests {

	private EventPrinter eventPrinter;

	@BeforeEach
	void mockWait() {
		this.eventPrinter = mock(EventPrinter.class);
	}

	@AfterEach
	void noMoreEventPrinterInteractions() {
		verifyNoMoreInteractions(this.eventPrinter);
	}

	/**
	 * v2 waiters stop at whichever of maxAttempts or waitTimeout comes first, and every generated
	 * CloudFormation waiter defaults to 120 attempts. Leaving that default silently caps a wait at
	 * 120 polls - roughly two minutes with the default interval - so a stack operation that takes
	 * longer aborts while CloudFormation is still working.
	 */
	@Test
	void waiterAttemptBudgetDoesNotEndTheWaitBeforeTheTimeout() {
		PollConfiguration pollConfiguration = PollConfiguration.builder()
				.timeout(Duration.ofHours(2))
				.pollInterval(Duration.ofSeconds(1))
				.build();

		WaiterOverrideConfiguration config = CloudFormationStack.waiterConfig(pollConfiguration);

		Assertions.assertThat(config.waitTimeout()).contains(Duration.ofHours(2));
		long pollsWithinTimeout = pollConfiguration.getTimeout().toMillis() / pollConfiguration.getPollInterval().toMillis();
		Assertions.assertThat(config.maxAttempts().orElse(0)).isGreaterThanOrEqualTo((int) pollsWithinTimeout);
	}

	/**
	 * pollInterval: 0 is documented as disabling event printing, and EventPrinter skips its loop for
	 * it - but it also reaches the waiter's backoff. FixedDelayBackoffStrategy accepts a zero delay
	 * rather than rejecting it, so combined with the unbounded attempt budget above the waiter would
	 * call DescribeStacks in a tight loop for the whole timeout. The backoff is floored instead.
	 */
	@Test
	void waiterBackoffIsFlooredWhenEventPrintingIsDisabled() {
		PollConfiguration pollConfiguration = PollConfiguration.builder()
				.timeout(Duration.ofMinutes(10))
				.pollInterval(Duration.ZERO)
				.build();

		WaiterOverrideConfiguration config = CloudFormationStack.waiterConfig(pollConfiguration);

		Assertions.assertThat(config.waitTimeout()).contains(Duration.ofMinutes(10));
		Assertions.assertThat(config.backoffStrategy())
				.contains(FixedDelayBackoffStrategy.create(Duration.ofSeconds(1)));
	}

	/**
	 * pollInterval is in milliseconds, so sub-second values are legal and must reach the waiter
	 * unchanged - the substitution above is only for the non-positive "event printing off" case.
	 * Rounding 250 ms up to a second would let the waiter notice completion up to 750 ms after
	 * EventPrinter's own loop already had.
	 */
	@Test
	void waiterBackoffHonoursSubSecondIntervalsExactly() {
		PollConfiguration pollConfiguration = PollConfiguration.builder()
				.timeout(Duration.ofMinutes(10))
				.pollInterval(Duration.ofMillis(250))
				.build();

		WaiterOverrideConfiguration config = CloudFormationStack.waiterConfig(pollConfiguration);

		Assertions.assertThat(config.backoffStrategy())
				.contains(FixedDelayBackoffStrategy.create(Duration.ofMillis(250)));
	}

	@Test
	void stackExists() {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()
		)).thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().build()
							  ).build()
		);
		assertThat(stack.exists(), is(true));
	}

	private CloudFormationStack newCloudFormationStack(CloudFormationClient client, String foo, TaskListener taskListener) {
		return new CloudFormationStack(client, foo, taskListener) {
			@Override
			protected EventPrinter getEventPrinter() {
				return eventPrinter;
			}
		};
	}

	@Test
	void stackDoesNotExists() {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("foo")
				.awsErrorDetails(AwsErrorDetails.builder().errorCode("ValidationError").errorMessage("stack foo does not exist").build())
				.build();
				when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()
		)).thenThrow(ex);
		Assertions.assertThat(stack.exists()).isFalse();
	}

	@Test
	void changeSetExists() {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).build()
		);
		Assertions.assertThat(stack.changeSetExists("bar")).isTrue();
	}

	@Test
	void executeChangeSetWithChanges() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).build()
		);

		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().stackStatus("CREATE_COMPLETE").outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);

		verify(client).executeChangeSet(any(ExecuteChangeSetRequest.class));
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	void doNotExecuteChangeSetIfNoChanges() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("the submitted information didn't contain changes").build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);
		verify(client, never()).executeChangeSet(any(ExecuteChangeSetRequest.class));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "false");
	}

	@Test
	void executeChangeSetIfNoChangesButSuccessfulStatus() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.CREATE_COMPLETE).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().stackStatus(StackStatus.CREATE_COMPLETE).outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);
		verify(client).executeChangeSet(any(ExecuteChangeSetRequest.class));
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz");
	}

	@Test
	void changeSetDoesNotExists() {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("foo")
				.awsErrorDetails(AwsErrorDetails.builder().errorCode("ValidationError").errorMessage("change set bar does not exist").build())
				.build();
				when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenThrow(ex);
		Assertions.assertThat(stack.changeSetExists("bar")).isFalse();
	}

	@Test
	void describeStack() {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());
		Assertions.assertThat(stack.describeOutputs()).isEqualTo(Collections.singletonMap(
				"bar", "baz"
		));
	}

	@Test
	void createNewStackChangeSet() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateChangeSetRequest.builder().changeSetType(ChangeSetType.CREATE).stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).changeSetName("c1").roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		verify(this.eventPrinter).waitAndPrintChangeSetEvents(eq("foo"), eq("c1"), any(), eq(PollConfiguration.DEFAULT));
	}

	@Test
	void createNewStackChangeSet_NoSubmittedChanges() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(any(DescribeStacksRequest.class))).thenReturn(DescribeStacksResponse.builder().build());
		when(client.describeChangeSet(any(DescribeChangeSetRequest.class))).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("The submitted information didn't contain changes").build()
		);
		doThrow(new ExecutionException(SdkClientException.create("foo")))
				.when(this.eventPrinter)
						.waitAndPrintChangeSetEvents(eq("foo"), eq("c1"),
								any(), eq(PollConfiguration.DEFAULT));

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null);
		verify(this.eventPrinter, atLeastOnce()).waitAndPrintChangeSetEvents(any(), any(), any(), any());
	}

	@Test
	void createNewStackChangeSet_NoUpdatesToBePerformed() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(any(DescribeStacksRequest.class))).thenReturn(DescribeStacksResponse.builder().build());
		when(client.describeChangeSet(any(DescribeChangeSetRequest.class))).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("No updates are to be performed").build()
		);
		doThrow(new ExecutionException(SdkClientException.create("foo")))
				.when(this.eventPrinter)
				.waitAndPrintChangeSetEvents(eq("foo"), eq("c1"),
						any(), eq(PollConfiguration.DEFAULT));

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null);
		verify(this.eventPrinter, atLeastOnce()).waitAndPrintChangeSetEvents(any(), any(), any(), any());
	}

	@Test
	void createNewStackChangeSet_UnknownWaiterError() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(any(DescribeStacksRequest.class))).thenReturn(DescribeStacksResponse.builder().build());
		when(client.describeChangeSet(any(DescribeChangeSetRequest.class))).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("someother failure").build()
			);
		doThrow(new ExecutionException(SdkClientException.create("foo")))
					.when(this.eventPrinter)
					.waitAndPrintChangeSetEvents(eq("foo"), eq("c1"),
							any(), eq(PollConfiguration.DEFAULT));
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		assertThrows(ExecutionException.class, () ->
				stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null));
		verify(this.eventPrinter, atLeastOnce()).waitAndPrintChangeSetEvents(any(), any(), any(), any());
	}

	@Test
	void updateStackWithStackChangeSet() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().stackStatus("CREATE_COMPLETE").build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.UPDATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateChangeSetRequest.builder().changeSetType(ChangeSetType.UPDATE).stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).changeSetName("c1").roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		verify(this.eventPrinter).waitAndPrintChangeSetEvents(eq("foo"), eq("c1"), any(), eq(PollConfiguration.DEFAULT));
	}

	@Test
	void createStackWithStackChangeSetReviewInProgress() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().stackStatus("REVIEW_IN_PROGRESS").build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.UPDATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateChangeSetRequest.builder().changeSetType(ChangeSetType.CREATE).stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).changeSetName("c1").roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		verify(this.eventPrinter).waitAndPrintChangeSetEvents(eq("foo"), eq("c1"), any(), eq(PollConfiguration.DEFAULT));
	}

	@Test
	void updateStack() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = RollbackConfiguration.builder().monitoringTimeInMinutes(10).build();
		Map<String, String> outputs = stack.update("templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackRequest.builder().stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).roleARN("myarn").rollbackConfiguration(rollbackConfig).build()
		);
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	void updateStackWithTemplateUrl() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = RollbackConfiguration.builder().monitoringTimeInMinutes(10).build();
		Map<String, String> outputs = stack.update(null, "bar", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackRequest.builder().stackName("foo").templateURL("bar").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).roleARN("myarn").rollbackConfiguration(rollbackConfig).build()
		);
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	void updateStackWithPreviousTemplate() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = RollbackConfiguration.builder().monitoringTimeInMinutes(10).build();
		Map<String, String> outputs = stack.update(null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackRequest.builder().stackName("foo").usePreviousTemplate(true).capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).roleARN("myarn").rollbackConfiguration(rollbackConfig).build()
		);
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	void createStack() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		Map<String, String> outputs = stack.create("templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", OnFailure.DO_NOTHING.toString(), null);

		ArgumentCaptor<CreateStackRequest> captor = ArgumentCaptor.forClass(CreateStackRequest.class);
		verify(client).createStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackRequest.builder().stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).timeoutInMinutes((int) PollConfiguration.DEFAULT.getTimeout().toMinutes()).onFailure(OnFailure.DO_NOTHING).roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	void createStackWithTemplateUrl() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		PollConfiguration pollConfiguration = PollConfiguration.builder()
				.timeout(Duration.ofMinutes(3))
				.pollInterval(Duration.ofSeconds(17))
				.build();
		Map<String, String> outputs = stack.create(null, "bar", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), pollConfiguration, "myarn", OnFailure.DO_NOTHING.toString(), true);

		ArgumentCaptor<CreateStackRequest> captor = ArgumentCaptor.forClass(CreateStackRequest.class);
		verify(client).createStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackRequest.builder().stackName("foo").enableTerminationProtection(true).templateURL("bar").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).timeoutInMinutes(3).onFailure(OnFailure.DO_NOTHING).roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(pollConfiguration));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	void createStackWithNoTemplate() {
		CloudFormationClient client = mock(CloudFormationClient.class);

		TaskListener taskListener = mock(TaskListener.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		assertThrows(IllegalArgumentException.class, () -> stack.create(null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", OnFailure.ROLLBACK.toString(), null));
		verifyNoInteractions(client);
	}

	@Test
	void deleteStack() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		when(client.describeStackEvents(any(DescribeStackEventsRequest.class))).thenReturn(DescribeStackEventsResponse.builder().build());
		when(client.describeStacks(any(DescribeStacksRequest.class))).thenReturn(DescribeStacksResponse.builder().build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.delete(PollConfiguration.DEFAULT, new String[]{"myresourcetoretain"}, "myarn", "myclientrequesttoken");

		ArgumentCaptor<DeleteStackRequest> captor = ArgumentCaptor.forClass(DeleteStackRequest.class);
		verify(client).deleteStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(DeleteStackRequest.builder().stackName("foo").clientRequestToken("myclientrequesttoken").roleARN("myarn").retainResources("myresourcetoretain").build()

		);
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(PollConfiguration.DEFAULT));
	}

	@Test
	void deleteStackByStackNameOnly() throws ExecutionException {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.delete(PollConfiguration.DEFAULT, null, null, null);

		ArgumentCaptor<DeleteStackRequest> captor = ArgumentCaptor.forClass(DeleteStackRequest.class);
		verify(client).deleteStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(DeleteStackRequest.builder().stackName("foo").build()

		);
		verify(this.eventPrinter).waitAndPrintStackEvents(eq("foo"), any(), eq(PollConfiguration.DEFAULT));
	}

	@Test
	void describeChangeSet() {
		TaskListener taskListener = mock(TaskListener.class);
		when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = mock(CloudFormationClient.class);
		DescribeChangeSetResponse expected = DescribeChangeSetResponse.builder().changes(
						Change.builder().build()
				).build();
		when(client.describeChangeSet(any(DescribeChangeSetRequest.class))).thenReturn(expected);

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		DescribeChangeSetResponse result = stack.describeChangeSet("bar");
		Assertions.assertThat(result).isSameAs(expected);

		ArgumentCaptor<DescribeChangeSetRequest> captor = ArgumentCaptor.forClass(DescribeChangeSetRequest.class);
		verify(client).describeChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		);
	}
}
