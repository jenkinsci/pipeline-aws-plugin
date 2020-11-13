package de.taimos.pipeline.aws.cloudformation;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.Change;
import com.amazonaws.services.cloudformation.model.ChangeSetStatus;
import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.CreateChangeSetRequest;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetRequest;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.ExecuteChangeSetRequest;
import com.amazonaws.services.cloudformation.model.OnFailure;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.RollbackConfiguration;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.StackStatus;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.services.cloudformation.waiters.AmazonCloudFormationWaiters;
import com.amazonaws.waiters.Waiter;

import hudson.model.TaskListener;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
		fullyQualifiedNames = "de.taimos.pipeline.aws.cloudformation.*"
)
@PowerMockIgnore("javax.crypto.*")
public class CloudformationStackTests {

	private EventPrinter eventPrinter;

	@Before
	public void mockWait() throws Exception {
		this.eventPrinter = Mockito.mock(EventPrinter.class);
		PowerMockito.whenNew(EventPrinter.class)
				.withAnyArguments()
				.thenReturn(this.eventPrinter);
	}

	@After
	public void noMoreEventPrinterInteractions() {
		Mockito.verifyNoMoreInteractions(this.eventPrinter);
	}

	@Test
	public void stackExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeStacks(new DescribeStacksRequest()
												   .withStackName("foo")
		)).thenReturn(new DescribeStacksResult()
							  .withStacks(new Stack()
							  )
		);
		Assertions.assertThat(stack.exists()).isTrue();
	}

	@Test
	public void stackDoesNotExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		AmazonCloudFormationException ex = new AmazonCloudFormationException("foo");
		ex.setErrorCode("ValidationError");
		ex.setErrorMessage("stack foo does not exist");
		Mockito.when(client.describeStacks(new DescribeStacksRequest()
												   .withStackName("foo")
		)).thenThrow(ex);
		Assertions.assertThat(stack.exists()).isFalse();
	}

	@Test
	public void changeSetExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(new DescribeChangeSetRequest()
													  .withStackName("foo")
													  .withChangeSetName("bar")
		)).thenReturn(new DescribeChangeSetResult()
							  .withChanges(new Change())
		);
		Assertions.assertThat(stack.changeSetExists("bar")).isTrue();
	}

	@Test
	public void executeChangeSetWithChanges() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(new DescribeChangeSetRequest()
													  .withStackName("foo")
													  .withChangeSetName("bar")
		)).thenReturn(new DescribeChangeSetResult()
							  .withChanges(new Change())
		);

		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withStackStatus("CREATE_COMPLETE").withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);

		Mockito.verify(client).executeChangeSet(Mockito.any(ExecuteChangeSetRequest.class));
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void doNotExecuteChangeSetIfNoChanges() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(new DescribeChangeSetRequest()
													  .withStackName("foo")
													  .withChangeSetName("bar")
		)).thenReturn(new DescribeChangeSetResult().withStatus(ChangeSetStatus.FAILED).withStatusReason("the submitted information didn't contain changes"));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);
		Mockito.verify(client, Mockito.never()).executeChangeSet(Mockito.any(ExecuteChangeSetRequest.class));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "false");
	}

	@Test
	public void executeChangeSetIfNoChangesButSuccessfulStatus() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(new DescribeChangeSetRequest()
				.withStackName("foo")
				.withChangeSetName("bar")
		)).thenReturn(new DescribeChangeSetResult().withStatus(ChangeSetStatus.CREATE_COMPLETE));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withStackStatus(StackStatus.CREATE_COMPLETE).withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		Map<String, String> outputs = stack.executeChangeSet("bar", PollConfiguration.DEFAULT);
		Mockito.verify(client).executeChangeSet(Mockito.any(ExecuteChangeSetRequest.class));
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz");
	}

	@Test
	public void changeSetDoesNotExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		AmazonCloudFormationException ex = new AmazonCloudFormationException("foo");
		ex.setErrorCode("ValidationError");
		ex.setErrorMessage("change set bar does not exist");
		Mockito.when(client.describeChangeSet(new DescribeChangeSetRequest()
													  .withStackName("foo")
													  .withChangeSetName("bar")
		)).thenThrow(ex);
		Assertions.assertThat(stack.changeSetExists("bar")).isFalse();
	}

	@Test
	public void describeStack() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));
		Assertions.assertThat(stack.describeOutputs()).isEqualTo(Collections.singletonMap(
				"bar", "baz"
		));
	}

	@Test
	public void createNewStackChangeSet() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult());

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.CREATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		Mockito.verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateChangeSetRequest()
																   .withChangeSetType(ChangeSetType.CREATE)
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.emptyList())
																   .withChangeSetName("c1")
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void updateStackWithStackChangeSet() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withStackStatus("CREATE_COMPLETE")));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.UPDATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		Mockito.verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateChangeSetRequest()
																   .withChangeSetType(ChangeSetType.UPDATE)
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.emptyList())
																   .withChangeSetName("c1")
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void createStackWithStackChangeSetReviewInProgress() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withStackStatus("REVIEW_IN_PROGRESS")));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, ChangeSetType.UPDATE, "myarn", null);

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		Mockito.verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateChangeSetRequest()
																   .withChangeSetType(ChangeSetType.CREATE)
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.emptyList())
																   .withChangeSetName("c1")
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void updateStack() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = new RollbackConfiguration().withMonitoringTimeInMinutes(10);
		Map<String, String> outputs = stack.update("templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackRequest()
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.emptyList())
																   .withRoleARN("myarn")
																   .withRollbackConfiguration(rollbackConfig)
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void updateStackWithTemplateUrl() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = new RollbackConfiguration().withMonitoringTimeInMinutes(10);
		Map<String, String> outputs = stack.update(null, "bar", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackRequest()
																   .withStackName("foo")
																   .withTemplateURL("bar")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.emptyList())
																   .withRoleARN("myarn")
																   .withRollbackConfiguration(rollbackConfig)
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void updateStackWithPreviousTemplate() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		RollbackConfiguration rollbackConfig = new RollbackConfiguration().withMonitoringTimeInMinutes(10);
		Map<String, String> outputs = stack.update(null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", rollbackConfig);

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackRequest()
																   .withStackName("foo")
																   .withUsePreviousTemplate(true)
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.emptyList())
																   .withRoleARN("myarn")
																   .withRollbackConfiguration(rollbackConfig)
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void createStack() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		Map<String, String> outputs = stack.create("templateBody", null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", OnFailure.DO_NOTHING.toString(), null);

		ArgumentCaptor<CreateStackRequest> captor = ArgumentCaptor.forClass(CreateStackRequest.class);
		Mockito.verify(client).createStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateStackRequest()
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.emptyList())
																   .withTimeoutInMinutes((int) PollConfiguration.DEFAULT.getTimeout().toMinutes())
																   .withOnFailure(OnFailure.DO_NOTHING)
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test
	public void createStackWithTemplateUrl() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));
		Mockito.when(client.describeStacks(new DescribeStacksRequest().withStackName("foo")))
				.thenReturn(new DescribeStacksResult().withStacks(new Stack().withOutputs(new Output().withOutputKey("bar").withOutputValue("baz"))));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		PollConfiguration pollConfiguration = PollConfiguration.builder()
				.timeout(Duration.ofMinutes(3))
				.pollInterval(Duration.ofSeconds(17))
				.build();
		Map<String, String> outputs = stack.create(null, "bar", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), pollConfiguration, "myarn", OnFailure.DO_NOTHING.toString(), true);

		ArgumentCaptor<CreateStackRequest> captor = ArgumentCaptor.forClass(CreateStackRequest.class);
		Mockito.verify(client).createStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateStackRequest()
																   .withStackName("foo")
																   .withEnableTerminationProtection(true)
																   .withTemplateURL("bar")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.emptyList())
																   .withTimeoutInMinutes(3)
																   .withOnFailure(OnFailure.DO_NOTHING)
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(pollConfiguration));
		Assertions.assertThat(outputs).containsEntry("bar", "baz").containsEntry("jenkinsStackUpdateStatus", "true");
	}

	@Test(expected = IllegalArgumentException.class)
	public void createStackWithNoTemplate() throws ExecutionException {
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		try {
			TaskListener taskListener = Mockito.mock(TaskListener.class);
			Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

			CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

			stack.create(null, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), PollConfiguration.DEFAULT, "myarn", OnFailure.ROLLBACK.toString(), null);
		} finally {
			Mockito.verifyZeroInteractions(client);
		}
	}

	@Test
	public void deleteStack() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.delete(PollConfiguration.DEFAULT, new String[]{"myresourcetoretain"}, "myarn", "myclientrequesttoken");

		ArgumentCaptor<DeleteStackRequest> captor = ArgumentCaptor.forClass(DeleteStackRequest.class);
		Mockito.verify(client).deleteStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new DeleteStackRequest()
																   .withStackName("foo")
																   .withClientRequestToken("myclientrequesttoken")
																   .withRoleARN("myarn")
																   .withRetainResources("myresourcetoretain")

		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void deleteStackByStackNameOnly() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.delete(PollConfiguration.DEFAULT, null, null, null);

		ArgumentCaptor<DeleteStackRequest> captor = ArgumentCaptor.forClass(DeleteStackRequest.class);
		Mockito.verify(client).deleteStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new DeleteStackRequest()
				.withStackName("foo")

		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(PollConfiguration.DEFAULT));
	}

	@Test
	public void describeChangeSet() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		DescribeChangeSetResult expected = new DescribeChangeSetResult()
				.withChanges(
						new Change()
				);
		Mockito.when(client.describeChangeSet(Mockito.any(DescribeChangeSetRequest.class))).thenReturn(expected);

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		DescribeChangeSetResult result = stack.describeChangeSet("bar");
		Assertions.assertThat(result).isSameAs(expected);

		ArgumentCaptor<DescribeChangeSetRequest> captor = ArgumentCaptor.forClass(DescribeChangeSetRequest.class);
		Mockito.verify(client).describeChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new DescribeChangeSetRequest()
																   .withStackName("foo")
																   .withChangeSetName("bar")
		);
	}
}
