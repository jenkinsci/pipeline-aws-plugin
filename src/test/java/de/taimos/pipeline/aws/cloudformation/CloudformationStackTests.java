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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;

public class CloudformationStackTests {

	private EventPrinter eventPrinter;

	@Before
	public void mockWait() {
		this.eventPrinter = Mockito.mock(EventPrinter.class);
	}

	@After
	public void noMoreEventPrinterInteractions() {
		Mockito.verifyNoMoreInteractions(this.eventPrinter);
	}

	/**
	 * v2 waiters stop at whichever of maxAttempts or waitTimeout comes first, and every generated
	 * CloudFormation waiter defaults to 120 attempts. Leaving that default silently caps a wait at
	 * 120 polls - roughly two minutes with the default interval - so a stack operation that takes
	 * longer aborts while CloudFormation is still working.
	 */
	@Test
	public void waiterAttemptBudgetDoesNotEndTheWaitBeforeTheTimeout() {
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
	public void waiterBackoffIsFlooredWhenEventPrintingIsDisabled() {
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
	public void waiterBackoffHonoursSubSecondIntervalsExactly() {
		PollConfiguration pollConfiguration = PollConfiguration.builder()
				.timeout(Duration.ofMinutes(10))
				.pollInterval(Duration.ofMillis(250))
				.build();

		WaiterOverrideConfiguration config = CloudFormationStack.waiterConfig(pollConfiguration);

		Assertions.assertThat(config.backoffStrategy())
				.contains(FixedDelayBackoffStrategy.create(Duration.ofMillis(250)));
	}

	@Test
	public void stackExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()
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
	public void stackDoesNotExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("foo")
				.awsErrorDetails(AwsErrorDetails.builder().errorCode("ValidationError").errorMessage("stack foo does not exist").build())
				.build();
				Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()
		)).thenThrow(ex);
		Assertions.assertThat(stack.exists()).isFalse();
	}

	@Test
	public void changeSetExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).build()
		);
		Assertions.assertThat(stack.changeSetExists("bar")).isTrue();
	}

	@Test
	public void executeChangeSetWithChanges() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).build()
		);

		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().stackStatus("CREATE_COMPLETE").outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);

		Mockito.verify(client).executeChangeSet(any(ExecuteChangeSetRequest.class));
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void doNotExecuteChangeSetIfNoChanges() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("the submitted information didn't contain changes").build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);
		Mockito.verify(client, Mockito.never()).executeChangeSet(any(ExecuteChangeSetRequest.class));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "false");
	}

	@Test
	public void executeChangeSetIfNoChangesButSuccessfulStatus() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.CREATE_COMPLETE).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().stackStatus(StackStatus.CREATE_COMPLETE).outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);
		Mockito.verify(client).executeChangeSet(any(ExecuteChangeSetRequest.class));
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz");
	}

	@Test
	public void changeSetDoesNotExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("foo")
				.awsErrorDetails(AwsErrorDetails.builder().errorCode("ValidationError").errorMessage("change set bar does not exist").build())
				.build();
				Mockito.when(client.describeChangeSet(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		)).thenThrow(ex);
		Assertions.assertThat(stack.changeSetExists("bar")).isFalse();
	}

	@Test
	public void describeStack() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());
		Assertions.assertThat(stack.describeOutputs()).isEqualTo(Collections.singletonMap(
				"bar", "baz"
		));
	}

	@Test
	public void createNewStackChangeSet() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		Mockito.verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateChangeSetRequest.builder().changeSetType(ChangeSetType.CREATE).stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).changeSetName("c1").roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		Mockito.verify(this.eventPrinter).waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void createNewStackChangeSet_NoSubmittedChanges() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(Mockito.any(DescribeStacksRequest.class))).thenReturn(DescribeStacksResponse.builder().build());
		Mockito.when(client.describeChangeSet(Mockito.any(DescribeChangeSetRequest.class))).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("The submitted information didn't contain changes").build()
		);
		Mockito.doThrow(new ExecutionException(SdkClientException.create("foo")))
				.when(this.eventPrinter)
						.waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"),
								Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null);
		Mockito.verify(this.eventPrinter, Mockito.atLeastOnce()).waitAndPrintChangeSetEvents(any(), any(), Mockito.any(), any());
	}

	@Test
	public void createNewStackChangeSet_NoUpdatesToBePerformed() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(Mockito.any(DescribeStacksRequest.class))).thenReturn(DescribeStacksResponse.builder().build());
		Mockito.when(client.describeChangeSet(Mockito.any(DescribeChangeSetRequest.class))).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("No updates are to be performed").build()
		);
		Mockito.doThrow(new ExecutionException(SdkClientException.create("foo")))
				.when(this.eventPrinter)
				.waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"),
						Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null);
		Mockito.verify(this.eventPrinter, Mockito.atLeastOnce()).waitAndPrintChangeSetEvents(any(), any(), Mockito.any(), any());
	}

	@Test(expected = ExecutionException.class)
	public void createNewStackChangeSet_UnknownWaiterError() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(Mockito.any(DescribeStacksRequest.class))).thenReturn(DescribeStacksResponse.builder().build());
		Mockito.when(client.describeChangeSet(Mockito.any(DescribeChangeSetRequest.class))).thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("someother failure").build()
		);
		Mockito.doThrow(new ExecutionException(SdkClientException.create("foo")))
				.when(this.eventPrinter)
				.waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"),
						Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		try {
			stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null);
		} finally {
			Mockito.verify(this.eventPrinter, Mockito.atLeastOnce()).waitAndPrintChangeSetEvents(any(), any(), Mockito.any(), any());
		}
	}

	@Test
	public void updateStackWithStackChangeSet() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().stackStatus("CREATE_COMPLETE").build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.UPDATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		Mockito.verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateChangeSetRequest.builder().changeSetType(ChangeSetType.UPDATE).stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).changeSetName("c1").roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		Mockito.verify(this.eventPrinter).waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void createStackWithStackChangeSetReviewInProgress() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().stackStatus("REVIEW_IN_PROGRESS").build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.UPDATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		Mockito.verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateChangeSetRequest.builder().changeSetType(ChangeSetType.CREATE).stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).changeSetName("c1").roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		Mockito.verify(this.eventPrinter).waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void updateStack() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = RollbackConfiguration.builder().monitoringTimeInMinutes(10).build();
		Map<String, String> outputs = stack.update("templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackRequest.builder().stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).roleARN("myarn").rollbackConfiguration(rollbackConfig).build()
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void updateStackWithTemplateUrl() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = RollbackConfiguration.builder().monitoringTimeInMinutes(10).build();
		Map<String, String> outputs = stack.update(null, "bar", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackRequest.builder().stackName("foo").templateURL("bar").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).roleARN("myarn").rollbackConfiguration(rollbackConfig).build()
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void updateStackWithPreviousTemplate() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = RollbackConfiguration.builder().monitoringTimeInMinutes(10).build();
		Map<String, String> outputs = stack.update(null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackRequest.builder().stackName("foo").usePreviousTemplate(true).capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).roleARN("myarn").rollbackConfiguration(rollbackConfig).build()
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void createStack() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		Map<String, String> outputs = stack.create("templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", OnFailure.DO_NOTHING.toString(), null);

		ArgumentCaptor<CreateStackRequest> captor = ArgumentCaptor.forClass(CreateStackRequest.class);
		Mockito.verify(client).createStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackRequest.builder().stackName("foo").templateBody("templateBody").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).timeoutInMinutes((int) PollConfiguration.DEFAULT.getTimeout().toMinutes()).onFailure(OnFailure.DO_NOTHING).roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void createStackWithTemplateUrl() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStacks(DescribeStacksRequest.builder().stackName("foo").build()))
				.thenReturn(DescribeStacksResponse.builder().stacks(Stack.builder().outputs(Output.builder().outputKey("bar").outputValue("baz").build()).build()).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		PollConfiguration pollConfiguration = PollConfiguration.builder()
				.timeout(Duration.ofMinutes(3))
				.pollInterval(Duration.ofSeconds(17))
				.build();
		Map<String, String> outputs = stack.create(null, "bar", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), pollConfiguration, "myarn", OnFailure.DO_NOTHING.toString(), true);

		ArgumentCaptor<CreateStackRequest> captor = ArgumentCaptor.forClass(CreateStackRequest.class);
		Mockito.verify(client).createStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackRequest.builder().stackName("foo").enableTerminationProtection(true).templateURL("bar").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(Collections.emptyList()).timeoutInMinutes(3).onFailure(OnFailure.DO_NOTHING).roleARN("myarn").notificationARNs(Collections.emptyList()).tags(Collections.emptyList()).build()
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(pollConfiguration));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createStackWithNoTemplate() throws ExecutionException {
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		try {
			TaskListener taskListener = Mockito.mock(TaskListener.class);
			Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());

			CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

			stack.create(null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", OnFailure.ROLLBACK.toString(), null);
		} finally {
			Mockito.verifyNoInteractions(client);
		}
	}

	@Test
	public void deleteStack() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());
		Mockito.when(client.describeStackEvents(Mockito.any(DescribeStackEventsRequest.class))).thenReturn(DescribeStackEventsResponse.builder().build());
		Mockito.when(client.describeStacks(Mockito.any(DescribeStacksRequest.class))).thenReturn(DescribeStacksResponse.builder().build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.delete(PollConfiguration.DEFAULT, new String[]{"myresourcetoretain"}, "myarn", "myclientrequesttoken");

		ArgumentCaptor<DeleteStackRequest> captor = ArgumentCaptor.forClass(DeleteStackRequest.class);
		Mockito.verify(client).deleteStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(DeleteStackRequest.builder().stackName("foo").clientRequestToken("myclientrequesttoken").roleARN("myarn").retainResources("myresourcetoretain").build()

		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void deleteStackByStackNameOnly() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		Mockito.when(client.waiter()).thenAnswer(invocation -> CloudFormationWaiter.builder().client(client).build());

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);

		stack.delete(PollConfiguration.DEFAULT, null, null, null);

		ArgumentCaptor<DeleteStackRequest> captor = ArgumentCaptor.forClass(DeleteStackRequest.class);
		Mockito.verify(client).deleteStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(DeleteStackRequest.builder().stackName("foo").build()

		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void describeChangeSet() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		CloudFormationClient client = Mockito.mock(CloudFormationClient.class);
		DescribeChangeSetResponse expected = DescribeChangeSetResponse.builder().changes(
						Change.builder().build()
				).build();
		Mockito.when(client.describeChangeSet(any(DescribeChangeSetRequest.class))).thenReturn(expected);

		CloudFormationStack stack = newCloudFormationStack(client, "foo", taskListener);
		DescribeChangeSetResponse result = stack.describeChangeSet("bar");
		Assertions.assertThat(result).isSameAs(expected);

		ArgumentCaptor<DescribeChangeSetRequest> captor = ArgumentCaptor.forClass(DescribeChangeSetRequest.class);
		Mockito.verify(client).describeChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(DescribeChangeSetRequest.builder().stackName("foo").changeSetName("bar").build()
		);
	}
}
