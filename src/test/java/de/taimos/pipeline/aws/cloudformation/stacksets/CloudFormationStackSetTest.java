package de.taimos.pipeline.aws.cloudformation.stacksets;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import de.taimos.pipeline.aws.cloudformation.PollConfiguration;
import hudson.model.TaskListener;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

public class CloudFormationStackSetTest {

	/**
	 * Nothing in this class legitimately takes more than a couple of seconds. The bound is here rather
	 * than on individual methods because the two many-polls tests are only finite thanks to an
	 * overridden sleep: if that seam is ever bypassed - the sleep inlined back into a loop, or
	 * sleepBetweenPolls made private, final or static - they would each wait 50k x 1s rather than fail,
	 * and a wedged build is much worse to diagnose than a red one.
	 *
	 * 30s against an observed ~2s for the slowest test here: enough headroom for the slower of the two
	 * CI platforms without letting a real slowdown burn two minutes per method before reporting.
	 */
	@Rule
	public Timeout timeout = Timeout.seconds(30);

	private CloudFormationClient client;
	private SleepStrategy sleepStrategy;
	private CloudFormationStackSet stackSet;

	@Before
	public void setup() {
		TaskListener listener = Mockito.mock(TaskListener.class);
		Mockito.when(listener.getLogger()).thenReturn(System.out);
		client = Mockito.mock(CloudFormationClient.class);
		sleepStrategy = Mockito.mock(SleepStrategy.class);
		stackSet = new CloudFormationStackSet(client, "foo", listener, sleepStrategy);
	}

	@Test
	public void stackSetExists() {
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenReturn(DescribeStackSetResponse.builder().stackSet(StackSet.builder().build()).build()
				);

		Assertions.assertThat(stackSet.exists()).isTrue();
	}

	@Test
	public void stackSetDoesNotExists() {
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("stack set does not exist")
				.awsErrorDetails(AwsErrorDetails.builder().errorCode("StackSetNotFoundException").errorMessage("stack set does not exist").build())
				.build();
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenThrow(ex);

		Assertions.assertThat(stackSet.exists()).isFalse();
	}

	@Test(expected = CloudFormationException.class)
	public void stackSetExistsError() {
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("stack set does not exist")
				.awsErrorDetails(AwsErrorDetails.builder().errorMessage("stack set does not exist").build())
				.build();
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenThrow(ex);

		stackSet.exists();
	}

	@Test
	public void createTemplateBody() {
		CreateStackSetResponse expected = CreateStackSetResponse.builder().build();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		CreateStackSetResponse result = stackSet.create("body", null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).templateBody("body").build()
		);
	}

	@Test
	public void createTemplateUrl() {
		CreateStackSetResponse expected = CreateStackSetResponse.builder().build();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		CreateStackSetResponse result = stackSet.create(null, "url", Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).templateURL("url").build()
		);
	}

	@Test(expected = IllegalArgumentException.class)
	public void createNoTemplate() {
		CreateStackSetResponse expected = CreateStackSetResponse.builder().build();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		stackSet.create(null, null, Collections.singletonList(parameter1), Collections.singletonList(tag1), null, null);
	}

	@Test
	public void createAdministratorRoleArn() {
		CreateStackSetResponse expected = CreateStackSetResponse.builder().build();
		Mockito.when(client.createStackSet(Mockito.any(CreateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		CreateStackSetResponse result = stackSet.create("body", null, Collections.singletonList(parameter1), Collections.singletonList(tag1), "foo", "baz");
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<CreateStackSetRequest> captor = ArgumentCaptor.forClass(CreateStackSetRequest.class);
		Mockito.verify(client).createStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(CreateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).administrationRoleARN("foo").executionRoleName("baz").tags(tag1).templateBody("body").build()
		);
	}

	@Test
	public void updateTemplateBody() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update("body", null, UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).templateBody("body").build()
		);
	}

	@Test
	public void updateTemplateUrl() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update(null, "url", UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).templateURL("url").build()
		);
	}

	@Test
	public void updateTemplateKeepPrevious() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenReturn(expected);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update(null, null, UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).usePreviousTemplate(true).build()
		);
	}

	@Test
	public void update_OperationInProgressException() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenThrow(OperationInProgressException.class)
				.thenReturn(expected);

		Mockito.when(this.sleepStrategy.calculateSleepDuration(Mockito.anyInt())).thenReturn(5L);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update(null, null, UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client, Mockito.times(2)).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).usePreviousTemplate(true).build()
		);
		Mockito.verify(this.sleepStrategy).calculateSleepDuration(1);
	}

	@Test
	public void update_StaleRequestException() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenThrow(StaleRequestException.class)
				.thenReturn(expected);

		Mockito.when(this.sleepStrategy.calculateSleepDuration(Mockito.anyInt())).thenReturn(5L);

		Parameter parameter1 = Parameter.builder().parameterKey("foo").parameterValue("bar").build();
		Tag tag1 = Tag.builder().key("bar").value("baz").build();

		UpdateStackSetResponse result = stackSet.update(null, null, UpdateStackSetRequest.builder().parameters(parameter1).tags(tag1).build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client, Mockito.times(2)).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).parameters(parameter1).tags(tag1).usePreviousTemplate(true).build()
		);
		Mockito.verify(this.sleepStrategy).calculateSleepDuration(1);
	}

	@Test
	public void update_TooManyOperations_LimitExceeded() throws InterruptedException {
		UpdateStackSetResponse expected = UpdateStackSetResponse.builder().build();
		Mockito.when(client.updateStackSet(Mockito.any(UpdateStackSetRequest.class)))
				.thenThrow((LimitExceededException) LimitExceededException.builder()
						.message("StackSet operations cannot involve more than 3500")
						.awsErrorDetails(AwsErrorDetails.builder()
								.errorMessage("StackSet operations cannot involve more than 3500")
								.build())
						.build())
				.thenReturn(expected);

		Mockito.when(this.sleepStrategy.calculateSleepDuration(Mockito.anyInt())).thenReturn(5L);

		UpdateStackSetResponse result = stackSet.update(null, null, UpdateStackSetRequest.builder().build());
		Assertions.assertThat(result).isSameAs(expected);
		ArgumentCaptor<UpdateStackSetRequest> captor = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		Mockito.verify(client, Mockito.times(2)).updateStackSet(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(UpdateStackSetRequest.builder().stackSetName("foo").capabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM, Capability.CAPABILITY_AUTO_EXPAND).usePreviousTemplate(true).build()
		);
		Mockito.verify(this.sleepStrategy).calculateSleepDuration(1);
	}

	@Test
	public void waitForStackStateStatus() throws InterruptedException {
		Mockito.when(client.describeStackSet(DescribeStackSetRequest.builder().stackSetName("foo").build()
		)).thenReturn(DescribeStackSetResponse.builder().stackSet(StackSet.builder().status(StackSetStatus.ACTIVE).build()
				).build()
		).thenReturn(DescribeStackSetResponse.builder().stackSet(StackSet.builder().status(StackSetStatus.DELETED).build()
				).build()
		);
		stackSet.waitForStackState(StackSetStatus.DELETED, Duration.ofMillis(5));

		Mockito.verify(client, Mockito.atLeast(2))
				.describeStackSet(Mockito.any(DescribeStackSetRequest.class));
	}

	/**
	 * Neither stack-set wait has a timeout, and a multi-account operation runs for hours, so the poll
	 * count is unbounded in practice. Both waits used to recurse once per poll, so a long wait died
	 * with StackOverflowError rather than returning.
	 *
	 * Run on a thread asking for a small stack *and* with a large poll count. Thread's stackSize is
	 * only a hint - HotSpot clamps it up to a platform minimum, so the usable depth stays partly
	 * ambient and CI covers two platforms (linux/JDK 21 and windows/JDK 17). Since the failure being
	 * guarded against is invisible when these pass, the margin has to come from the poll count, not
	 * from the stack request. The invocation cost that a big count used to carry is gone because these
	 * two use their own stubOnly mock, which records nothing.
	 */
	private static final int POLLS = 50_000;
	private static final int SMALL_STACK_BYTES = 128 * 1024;

	/**
	 * A stub-only client for the two high-count tests: the shared field is verified by other tests, so
	 * it has to keep recording, but 50k recorded invocations per test is pure waste.
	 *
	 * The overridden sleep is load-bearing, not an optimisation. The callers pass the default poll
	 * interval of one second, and the floor admits no cheaper value, so 50k real sleeps would be
	 * roughly fourteen hours per test. The class timeout above is what turns losing this override into
	 * a failure rather than a hung build.
	 */
	private CloudFormationStackSet nonSleepingStackSet(CloudFormationClient stubOnlyClient, int[] sleeps) {
		// discard the per-poll progress lines rather than putting 50k of them on the build log
		TaskListener quietListener = Mockito.mock(TaskListener.class, Mockito.withSettings().stubOnly());
		Mockito.when(quietListener.getLogger()).thenReturn(new PrintStream(OutputStream.nullOutputStream(), false));
		return new CloudFormationStackSet(stubOnlyClient, "foo", quietListener, sleepStrategy) {
			@Override
			void sleepBetweenPolls(Duration pollInterval) {
				// counted rather than performed: the clock is not what these tests are measuring, but
				// that the loop waits between polls at all is - without the count, deleting the
				// sleepBetweenPolls call from either wait leaves the whole suite green, which is a
				// tight DescribeStackSet loop with no throttling retry behind it.
				sleeps[0]++;
			}
		};
	}

	/**
	 * The stack-set side of the shared substitution. PollConfigurationTest covers the helper itself and
	 * CloudformationStackTests covers the waiter call site; without this, removing
	 * effectivePollInterval from sleepBetweenPolls would leave the suite green and quietly restore a
	 * tight DescribeStackSet loop on pollInterval: 0 - which waitForStackState has no throttling retry
	 * for.
	 */
	@Test
	public void aNonPositivePollIntervalIsSubstitutedHereToo() {
		Assertions.assertThat(CloudFormationStackSet.pollSleepMillis(Duration.ZERO)).isEqualTo(1000L);
		Assertions.assertThat(CloudFormationStackSet.pollSleepMillis(Duration.ofMillis(-5))).isEqualTo(1000L);
		// a sub-millisecond interval rounds to zero milliseconds, so it counts as disabled rather than
		// sleeping for no time at all
		Assertions.assertThat(CloudFormationStackSet.pollSleepMillis(Duration.ofNanos(1))).isEqualTo(1000L);
		Assertions.assertThat(CloudFormationStackSet.pollSleepMillis(Duration.ofMillis(250))).isEqualTo(250L);
	}

	private static void runWithASmallStack(ThrowingRunnable body) throws Throwable {
		Throwable[] thrown = new Throwable[1];
		Thread thread = new Thread(null, () -> {
			try {
				body.run();
			} catch (Throwable t) {
				thrown[0] = t;
			}
		}, "poll", SMALL_STACK_BYTES);
		// daemon, so that if the timeout below fires while this thread is sleeping - the seam-bypassed
		// case - the orphan cannot hold the JVM open after the test has been reported
		thread.setDaemon(true);
		thread.start();
		try {
			thread.join();
		} catch (InterruptedException e) {
			thread.interrupt();
			throw e;
		}
		if (thrown[0] != null) {
			throw thrown[0];
		}
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	public void waitForStackStateSurvivesManyPolls() throws Throwable {
		// ACTIVE -> DELETED is the transition this wait polls through, so it is the direction that
		// exercises the loop; the losing branch covers the other one.
		DescribeStackSetResponse pending = DescribeStackSetResponse.builder()
				.stackSet(StackSet.builder().status(StackSetStatus.ACTIVE).build()).build();
		DescribeStackSetResponse done = DescribeStackSetResponse.builder()
				.stackSet(StackSet.builder().status(StackSetStatus.DELETED).build()).build();
		int[] calls = {0};
		CloudFormationClient stubOnly = Mockito.mock(CloudFormationClient.class, Mockito.withSettings().stubOnly());
		Mockito.when(stubOnly.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenAnswer(invocation -> ++calls[0] < POLLS ? pending : done);
		int[] sleeps = {0};
		CloudFormationStackSet subject = this.nonSleepingStackSet(stubOnly, sleeps);

		runWithASmallStack(() -> subject.waitForStackState(StackSetStatus.DELETED, PollConfiguration.DEFAULT.getPollInterval()));

		Assertions.assertThat(calls[0]).isEqualTo(POLLS);
		// one wait between each pair of polls
		Assertions.assertThat(sleeps[0]).isEqualTo(POLLS - 1);
	}

	@Test
	public void waitForOperationToCompleteSurvivesManyPolls() throws Throwable {
		String operationId = UUID.randomUUID().toString();
		DescribeStackSetOperationResponse running = DescribeStackSetOperationResponse.builder()
				.stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.RUNNING).build()).build();
		DescribeStackSetOperationResponse done = DescribeStackSetOperationResponse.builder()
				.stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.SUCCEEDED).build()).build();
		int[] calls = {0};
		CloudFormationClient stubOnly = Mockito.mock(CloudFormationClient.class, Mockito.withSettings().stubOnly());
		Mockito.when(stubOnly.describeStackSetOperation(Mockito.any(DescribeStackSetOperationRequest.class)))
				.thenAnswer(invocation -> ++calls[0] < POLLS ? running : done);
		int[] sleeps = {0};
		CloudFormationStackSet subject = this.nonSleepingStackSet(stubOnly, sleeps);

		runWithASmallStack(() -> subject.waitForOperationToComplete(operationId, PollConfiguration.DEFAULT.getPollInterval()));

		Assertions.assertThat(calls[0]).isEqualTo(POLLS);
		// one wait between each pair of polls
		Assertions.assertThat(sleeps[0]).isEqualTo(POLLS - 1);
	}

	/**
	 * DELETED is terminal, so waiting for ACTIVE has to fail rather than poll forever - as a loop it
	 * would otherwise hold an executor until the build was aborted.
	 */
	@Test
	public void waitForStackStateFailsOnATerminalStatusItIsNotWaitingFor() {
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenReturn(DescribeStackSetResponse.builder()
						.stackSet(StackSet.builder().status(StackSetStatus.DELETED).build()).build());

		Assertions.assertThatThrownBy(() -> stackSet.waitForStackState(StackSetStatus.ACTIVE, Duration.ZERO))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DELETED");
	}

	/**
	 * A status the pinned SDK does not model arrives as UNKNOWN_TO_SDK_VERSION, which must not be
	 * polled forever either.
	 */
	@Test
	public void waitForStackStateFailsOnAnUnmodelledStatus() {
		Mockito.when(client.describeStackSet(Mockito.any(DescribeStackSetRequest.class)))
				.thenReturn(DescribeStackSetResponse.builder()
						.stackSet(StackSet.builder().status("SOMETHING_NEW").build()).build());

		Assertions.assertThatThrownBy(() -> stackSet.waitForStackState(StackSetStatus.ACTIVE, Duration.ZERO))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SOMETHING_NEW");
	}

	@Test
	public void waitForOperationToComplete() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		Mockito.when(client.describeStackSetOperation(DescribeStackSetOperationRequest.builder().stackSetName("foo").operationId(operationId).build()
		)).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.RUNNING).build()
				).build()
		).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.SUCCEEDED).build()
				).build()
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test
	public void waitForOperationToCompleteWithThrottle() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder()
				.message("error")
				.awsErrorDetails(AwsErrorDetails.builder().errorCode("Throttling").errorMessage("error").build())
				.build();
		Mockito.when(client.describeStackSetOperation(DescribeStackSetOperationRequest.builder().stackSetName("foo").operationId(operationId).build()
		)).thenThrow(ex)
		.thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.RUNNING).build()
				).build()
		).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.SUCCEEDED).build()
				).build()
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test(expected = StackSetOperationFailedException.class)
	public void waitForOperationToCompleteFailure() throws InterruptedException {
		String operationId = UUID.randomUUID().toString();
		Mockito.when(client.describeStackSetOperation(DescribeStackSetOperationRequest.builder().stackSetName("foo").operationId(operationId).build()
		)).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.RUNNING).build()
				).build()
		).thenReturn(DescribeStackSetOperationResponse.builder().stackSetOperation(StackSetOperation.builder().status(StackSetOperationStatus.FAILED).build()
				).build()
		);
		stackSet.waitForOperationToComplete(operationId, Duration.ofMillis(5));
	}

	@Test
	public void delete() {
		stackSet.delete();
		Mockito.verify(client).deleteStackSet(DeleteStackSetRequest.builder().stackSetName("foo").build()
		);
	}
}
