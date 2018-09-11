package de.taimos.pipeline.aws.cloudformation.stacksets;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.*;
import hudson.model.TaskListener;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

public class CloudFormationStackSetTest {

	private TaskListener listener;
	private AmazonCloudFormation client;
	private SleepStrategy sleepStrategy;
	private CloudFormationStackSet stackSet;

	@Before
	public void setup() {
		listener = Mockito.mock(TaskListener.class);
		Mockito.when(listener.getLogger()).thenReturn(System.out);
		client = Mockito.mock(AmazonCloudFormation.class);
		sleepStrategy = Mockito.mock(SleepStrategy.class);
		stackSet = new CloudFormationStackSet(client, "foo", listener, sleepStrategy);
	}

	@Test
	public void stackSetExists() {
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenReturn(new DescribeStackSetResult()
						.withStackSet(new StackSet())
				);

		Assertions.assertThat(stackSet.exists()).isTrue();
	}

	@Test
	public void stackSetDoesNotExists() {
		AmazonCloudFormationException ex = new AmazonCloudFormationException("stack set does not exist");
		ex.setErrorCode("StackSetNotFoundException");
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenThrow(ex);

		Assertions.assertThat(stackSet.exists()).isFalse();
	}

	@Test(expected = AmazonCloudFormationException.class)
	public void stackSetExistsError() {
		AmazonCloudFormationException ex = new AmazonCloudFormationException("stack set does not exist");
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenThrow(ex);

		stackSet.exists();
	}

	@Test
	public void createTemplateBody() {
		CreateStackSetResult expected = new CreateStackSetResult();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		CreateStackSetResult result = stackSet.create("body", null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withTags(tag1)
				.withTemplateBody("body")
		);
	}

	@Test
	public void createTemplateUrl() {
		CreateStackSetResult expected = new CreateStackSetResult();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		CreateStackSetResult result = stackSet.create(null, "url", Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withTags(tag1)
				.withTemplateURL("url")
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNoTemplate() {
		CreateStackSetResult expected = new CreateStackSetResult();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		stackSet.create(null, null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
	}

	@Test
	public void createAdministratorRoleArn() {
		CreateStackSetResult expected = new CreateStackSetResult();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		CreateStackSetResult result = stackSet.create("body", null, Collections.singletonList(parameter1), Collections.singletonList(tag1), "foo", "baz");
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new CreateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withAdministrationRoleARN("foo")
				.withExecutionRoleName("baz")
				.withTags(tag1)
				.withTemplateBody("body")
		);
	}

	@Test
	public void updateTemplateBody() throws InterruptedException {
		UpdateStackSetResult expected = new UpdateStackSetResult();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		UpdateStackSetResult result = stackSet.update("body", null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withTags(tag1)
				.withTemplateBody("body")
		);
	}

	@Test
	public void updateTemplateUrl() throws InterruptedException {
		UpdateStackSetResult expected = new UpdateStackSetResult();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		UpdateStackSetResult result = stackSet.update(null, "url", Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withTags(tag1)
				.withTemplateURL("url")
		);
	}

	@Test
	public void updateAdministratorRoleArn() throws InterruptedException {
		UpdateStackSetResult expected = new UpdateStackSetResult();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		UpdateStackSetResult result = stackSet.update("body", null, Collections.singletonList(parameter1), Collections.singletonList(tag1), "bar", "baz");
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withAdministrationRoleARN("bar")
				.withExecutionRoleName("baz")
				.withTags(tag1)
				.withTemplateBody("body")
		);
	}

	@Test
	public void updateTemplateKeepPrevious() throws InterruptedException {
		UpdateStackSetResult expected = new UpdateStackSetResult();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		UpdateStackSetResult result = stackSet.update(null, null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withTags(tag1)
				.withUsePreviousTemplate(true)
		);
	}

	@Test
	public void update_OperationInProgressException() throws InterruptedException {
		UpdateStackSetResult expected = new UpdateStackSetResult();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenThrow(OperationInProgressException.class)
				.thenReturn(expected);

		Mockito.when(this.sleepStrategy.calculateSleepDuration(Mockito.anyInt())).thenReturn(5L);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		UpdateStackSetResult result = stackSet.update(null, null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client, Mockito.times(2)).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withTags(tag1)
				.withUsePreviousTemplate(true)
		);
		Mockito.verify(this.sleepStrategy).calculateSleepDuration(1);
	}

	@Test
	public void update_StaleRequestException() throws InterruptedException {
		UpdateStackSetResult expected = new UpdateStackSetResult();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenThrow(StaleRequestException.class)
				.thenReturn(expected);

		Mockito.when(this.sleepStrategy.calculateSleepDuration(Mockito.anyInt())).thenReturn(5L);

		Parameter parameter1 = new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar");
		Tag tag1 = new Tag()
				.withKey("bar")
				.withValue("baz");

		UpdateStackSetResult result = stackSet.update(null, null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client, Mockito.times(2)).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new UpdateStackSetRequest()
				.withStackSetName("foo")
				.withCapabilities(Capability.values())
				.withParameters(parameter1)
				.withTags(tag1)
				.withUsePreviousTemplate(true)
		);
		Mockito.verify(this.sleepStrategy).calculateSleepDuration(1);
	}

	@Test
	public void waitForStackStateStatus() throws InterruptedException {
		Mockito.when(client.describeStackSet(new DescribeStackSetRequest()
				.withStackSetName("foo")
		)).thenReturn(new DescribeStackSetResult()
				.withStackSet(new StackSet()
						.withStatus(StackSetStatus.ACTIVE)
				)
		).thenReturn(new DescribeStackSetResult()
				.withStackSet(new StackSet()
						.withStatus(StackSetStatus.DELETED)
				)
		);
		stackSet.waitForStackState(StackSetStatus.DELETED, Duration.ofMillis(5));

		Mockito.verify(client, Mockito.atLeast(2))
				.describeStackSet(Mockito.any(DescribeStackSetRequest.class));
	}

	@Test
	public void waitForOperationToComplete() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		Mockito.when(client.describeStackSetOperation(new DescribeStackSetOperationRequest()
				.withStackSetName("foo")
				.withOperationId(operationId)
		)).thenReturn(new DescribeStackSetOperationResult()
				.withStackSetOperation(new StackSetOperation()
						.withStatus(StackSetOperationStatus.RUNNING)
				)
		).thenReturn(new DescribeStackSetOperationResult()
				.withStackSetOperation(new StackSetOperation()
						.withStatus(StackSetOperationStatus.SUCCEEDED)
				)
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test
	public void waitForOperationToCompleteWithThrottle() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		AmazonCloudFormationException ex = new AmazonCloudFormationException("error");
		ex.setErrorCode("Throttling");
		Mockito.when(client.describeStackSetOperation(new DescribeStackSetOperationRequest()
				.withStackSetName("foo")
				.withOperationId(operationId)
		)).thenThrow(ex)
		.thenReturn(new DescribeStackSetOperationResult()
				.withStackSetOperation(new StackSetOperation()
						.withStatus(StackSetOperationStatus.RUNNING)
				)
		).thenReturn(new DescribeStackSetOperationResult()
				.withStackSetOperation(new StackSetOperation()
						.withStatus(StackSetOperationStatus.SUCCEEDED)
				)
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test(expected = StackSetOperationFailedException.class)
	public void waitForOperationToCompleteFailure() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		Mockito.when(client.describeStackSetOperation(new DescribeStackSetOperationRequest()
				.withStackSetName("foo")
				.withOperationId(operationId)
		)).thenReturn(new DescribeStackSetOperationResult()
				.withStackSetOperation(new StackSetOperation()
						.withStatus(StackSetOperationStatus.RUNNING)
				)
		).thenReturn(new DescribeStackSetOperationResult()
				.withStackSetOperation(new StackSetOperation()
						.withStatus(StackSetOperationStatus.FAILED)
				)
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test
	public void delete() {
		stackSet.delete();
		Mockito.verify(client).deleteStackSet(new DeleteStackSetRequest()
				.withStackSetName("foo")
		);
	}
}
