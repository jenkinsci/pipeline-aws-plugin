package de.taimos.pipeline.aws.cloudformation;

import java.util.Collections;
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
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.Tag;
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
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(new DescribeChangeSetRequest()
													  .withStackName("foo")
													  .withChangeSetName("bar")
		)).thenReturn(new DescribeChangeSetResult()
							  .withChanges(new Change())
		);

		Mockito.when(client.describeStacks(new DescribeStacksRequest()
												   .withStackName("foo")
		)).thenReturn(new DescribeStacksResult()
							  .withStacks(new Stack())
		);

		stack.executeChangeSet("bar", 25);

		Mockito.verify(client).executeChangeSet(Mockito.any(ExecuteChangeSetRequest.class));
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(25L));
	}

	@Test
	public void noExecuteChangeSetIfNoChanges() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeChangeSet(new DescribeChangeSetRequest()
													  .withStackName("foo")
													  .withChangeSetName("bar")
		)).thenReturn(new DescribeChangeSetResult());

		stack.executeChangeSet("bar", 1000L);
		Mockito.verify(client, Mockito.never()).executeChangeSet(Mockito.any(ExecuteChangeSetRequest.class));
	}

	@Test
	public void changeSetDoesNotExists() {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
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
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);
		Mockito.when(client.describeStacks(new DescribeStacksRequest()
												   .withStackName("foo")
		)).thenReturn(new DescribeStacksResult()
							  .withStacks(new Stack()
												  .withOutputs(
														  new Output()
																  .withOutputKey("bar")
																  .withOutputValue("baz")
												  )
							  )
		);
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

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.<Parameter>emptyList(), Collections.<Tag>emptyList(), 25, ChangeSetType.CREATE, "myarn");

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		Mockito.verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateChangeSetRequest()
																   .withChangeSetType(ChangeSetType.CREATE)
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.<Parameter>emptyList())
																   .withChangeSetName("c1")
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"), Mockito.any(Waiter.class), Mockito.eq(25L));
	}

	@Test
	public void updateStackWithStackChangeSet() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.createChangeSet("c1", "templateBody", null, Collections.<Parameter>emptyList(), Collections.<Tag>emptyList(), 25, ChangeSetType.UPDATE, "myarn");

		ArgumentCaptor<CreateChangeSetRequest> captor = ArgumentCaptor.forClass(CreateChangeSetRequest.class);
		Mockito.verify(client).createChangeSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateChangeSetRequest()
																   .withChangeSetType(ChangeSetType.UPDATE)
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.<Parameter>emptyList())
																   .withChangeSetName("c1")
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintChangeSetEvents(Mockito.eq("foo"), Mockito.eq("c1"), Mockito.any(Waiter.class), Mockito.eq(25L));
	}

	@Test
	public void updateStack() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.update("templateBody", null, Collections.<Parameter>emptyList(), Collections.<Tag>emptyList(), 25, "myarn");

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackRequest()
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.<Parameter>emptyList())
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(25L));
	}

	@Test
	public void updateStackWithTemplateUrl() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.update(null, "bar", Collections.<Parameter>emptyList(), Collections.<Tag>emptyList(), 21, "myarn");

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackRequest()
																   .withStackName("foo")
																   .withTemplateURL("bar")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.<Parameter>emptyList())
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(21L));
	}

	@Test
	public void updateStackWithPreviousTemplate() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.update(null, null, Collections.<Parameter>emptyList(), Collections.<Tag>emptyList(), 12, "myarn");

		ArgumentCaptor<UpdateStackRequest> captor = ArgumentCaptor.forClass(UpdateStackRequest.class);
		Mockito.verify(client).updateStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackRequest()
																   .withStackName("foo")
																   .withUsePreviousTemplate(true)
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.<Parameter>emptyList())
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(12L));
	}

	@Test
	public void createStack() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.create("templateBody", null, Collections.<Parameter>emptyList(), Collections.<Tag>emptyList(), 7, 25, "myarn", OnFailure.DO_NOTHING.toString());

		ArgumentCaptor<CreateStackRequest> captor = ArgumentCaptor.forClass(CreateStackRequest.class);
		Mockito.verify(client).createStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateStackRequest()
																   .withStackName("foo")
																   .withTemplateBody("templateBody")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.<Parameter>emptyList())
																   .withTimeoutInMinutes(7)
																   .withOnFailure(OnFailure.DO_NOTHING)
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(25L));
	}

	@Test
	public void createStackWithTemplateUrl() throws ExecutionException {
		TaskListener taskListener = Mockito.mock(TaskListener.class);
		Mockito.when(taskListener.getLogger()).thenReturn(System.out);
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

		CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

		stack.create(null, "bar", Collections.<Parameter>emptyList(), Collections.<Tag>emptyList(), 3, 21, "myarn", OnFailure.DO_NOTHING.toString());

		ArgumentCaptor<CreateStackRequest> captor = ArgumentCaptor.forClass(CreateStackRequest.class);
		Mockito.verify(client).createStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateStackRequest()
																   .withStackName("foo")
																   .withTemplateURL("bar")
																   .withCapabilities(Capability.values())
																   .withParameters(Collections.<Parameter>emptyList())
																   .withTimeoutInMinutes(3)
																   .withOnFailure(OnFailure.DO_NOTHING)
																   .withRoleARN("myarn")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(21L));
	}

	@Test(expected = IllegalArgumentException.class)
	public void createStackWithNoTemplate() throws ExecutionException {
		AmazonCloudFormation client = Mockito.mock(AmazonCloudFormation.class);
		try {
			TaskListener taskListener = Mockito.mock(TaskListener.class);
			Mockito.when(client.waiters()).thenReturn(new AmazonCloudFormationWaiters(client));

			CloudFormationStack stack = new CloudFormationStack(client, "foo", taskListener);

			stack.create(null, null, Collections.<Parameter>emptyList(), Collections.<Tag>emptyList(), 3, 21, "myarn", OnFailure.ROLLBACK.toString());
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

		stack.delete(7);

		ArgumentCaptor<DeleteStackRequest> captor = ArgumentCaptor.forClass(DeleteStackRequest.class);
		Mockito.verify(client).deleteStack(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new DeleteStackRequest()
																   .withStackName("foo")
		);
		Mockito.verify(this.eventPrinter).waitAndPrintStackEvents(Mockito.eq("foo"), Mockito.any(Waiter.class), Mockito.eq(7L));
	}
}
