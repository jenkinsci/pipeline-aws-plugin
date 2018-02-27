package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.Change;
import com.amazonaws.services.cloudformation.model.ChangeSetStatus;
import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
		value = AWSClientFactory.class,
		fullyQualifiedNames = "de.taimos.pipeline.aws.cloudformation.*"
)
@PowerMockIgnore("javax.crypto.*")
public class CFNCreateChangeSetTests {

	@Rule
	private JenkinsRule jenkinsRule = new JenkinsRule();
	private CloudFormationStack stack;

	@Before
	public void setupSdk() throws Exception {
		this.stack = Mockito.mock(CloudFormationStack.class);
		PowerMockito.mockStatic(AWSClientFactory.class);
		PowerMockito.whenNew(CloudFormationStack.class)
				.withAnyArguments()
				.thenReturn(this.stack);
		AmazonCloudFormation cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(EnvVars.class)))
				.thenReturn(cloudFormation);
	}

	@Test
	public void createChangeSetStackParametersFromMap() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(true);
		Mockito.when(stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', params: ['foo': 'bar', 'baz': 'true'])\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("changesCount=1", run);

		PowerMockito.verifyNew(CloudFormationStack.class, Mockito.atLeastOnce()).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.anyString(), Mockito.anyString(), Mockito.eq(Arrays.asList(
				new Parameter().withParameterKey("foo").withParameterValue("bar"),
				new Parameter().withParameterKey("baz").withParameterValue("true")
		)), Mockito.anyCollectionOf(Tag.class), Mockito.anyInt(), Mockito.eq(ChangeSetType.UPDATE), Mockito.anyString());
	}

	@Test
	public void createChangeSetStackExists() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(true);
		Mockito.when(stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("changesCount=1", run);

		PowerMockito.verifyNew(CloudFormationStack.class, Mockito.atLeastOnce()).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.anyString(), Mockito.anyString(), Mockito.anyCollectionOf(Parameter.class), Mockito.anyCollectionOf(Tag.class), Mockito.anyInt(), Mockito.eq(ChangeSetType.UPDATE), Mockito.anyString());
	}

	@Test
	public void createChangeSetWithRawTemplate() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(true);
		Mockito.when(stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', template: 'foobaz')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("changesCount=1", run);

		PowerMockito.verifyNew(CloudFormationStack.class, Mockito.atLeastOnce()).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.eq("foobaz"), Mockito.anyString(), Mockito.anyCollectionOf(Parameter.class), Mockito.anyCollectionOf(Tag.class), Mockito.anyInt(), Mockito.eq(ChangeSetType.UPDATE), Mockito.anyString());
	}

	@Test
	public void updateChangeSetWithRawTemplate() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(false);
		Mockito.when(stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', template: 'foobaz')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("changesCount=1", run);

		PowerMockito.verifyNew(CloudFormationStack.class, Mockito.atLeastOnce()).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.eq("foobaz"), Mockito.anyString(), Mockito.anyCollectionOf(Parameter.class), Mockito.anyCollectionOf(Tag.class), Mockito.anyInt(), Mockito.eq(ChangeSetType.CREATE), Mockito.anyString());
	}

	@Test
	public void createChangeSetStackFailure() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(true);
		Mockito.when(stack.describeChangeSet("bar"))
				.thenReturn(new DescribeChangeSetResult()
						.withStatus(ChangeSetStatus.FAILED)
				);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')\n"
				+ "}\n", true)
		);
		jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
	}

	@Test
	public void createEmptyChangeSet() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(true);
		Mockito.when(stack.describeChangeSet("bar"))
				.thenReturn(new DescribeChangeSetResult()
						.withStatus(ChangeSetStatus.FAILED)
						.withStatusReason("The submitted information didn't contain changes. Submit different information to create a change set.")
				);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("changesCount=0", run);

	}

	@Test
	public void createChangeSetStackDoesNotExist() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(false);
		Mockito.when(stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		jenkinsRule.assertLogContains("changesCount=1", run);

		PowerMockito.verifyNew(CloudFormationStack.class, Mockito.atLeastOnce()).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.anyString(), Mockito.anyString(), Mockito.anyCollectionOf(Parameter.class), Mockito.anyCollectionOf(Tag.class), Mockito.anyInt(), Mockito.eq(ChangeSetType.CREATE), Mockito.anyString());
	}

}
