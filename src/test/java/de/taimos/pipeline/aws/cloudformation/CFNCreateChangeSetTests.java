package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.Change;
import com.amazonaws.services.cloudformation.model.ChangeSetStatus;
import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.DescribeChangeSetResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import hudson.model.Result;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.nullable;

public class CFNCreateChangeSetTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private CloudFormationStack stack;

	@Before
	public void setupSdk() throws Exception {
		this.stack = Mockito.mock(CloudFormationStack.class);
		AmazonCloudFormation cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		AWSClientFactory.setFactoryDelegate((x) -> cloudFormation);
		AWSUtilFactory.setStackSupplier((s) -> {
			assertEquals("foo", s);
			return stack;
		});
	}

	@Test
	public void createChangeSetStackParametersFromMap() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', params: ['foo': 'bar', 'baz': 'true'])\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"),
				nullable(String.class), nullable(String.class), Mockito.eq(Arrays.asList(
				new Parameter().withParameterKey("foo").withParameterValue("bar"),
				new Parameter().withParameterKey("baz").withParameterValue("true")
		)), Mockito.anyCollection(), Mockito.anyCollection(), Mockito.any(PollConfiguration.class), Mockito.eq(ChangeSetType.UPDATE), nullable(String.class),
												   Mockito.any());
	}

	@Test
	public void createChangeSetStackExists() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), nullable(String.class), nullable(String.class),
				Mockito.anyCollection(), Mockito.anyCollection(), Mockito.anyCollection(),
				Mockito.any(PollConfiguration.class), Mockito.eq(ChangeSetType.UPDATE), nullable(String.class), Mockito.any());
	}

	@Test
	public void createChangeSetWithRawTemplate() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', template: 'foobaz')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.eq("foobaz"), nullable(String.class),
				Mockito.anyCollection(), Mockito.anyCollection(), Mockito.anyCollection(),
				Mockito.any(PollConfiguration.class), Mockito.eq(ChangeSetType.UPDATE), nullable(String.class), Mockito.any());
	}

	@Test
	public void updateChangeSetWithRawTemplate() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(false);
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', template: 'foobaz')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.eq("foobaz"), nullable(String.class),
				Mockito.anyCollection(), Mockito.anyCollection(), Mockito.anyCollection(),
				Mockito.any(PollConfiguration.class), Mockito.eq(ChangeSetType.CREATE), nullable(String.class), Mockito.any());
	}

	@Test
	public void createChangeSetStackFailure() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeChangeSet("bar"))
				.thenReturn(new DescribeChangeSetResult()
						.withStatus(ChangeSetStatus.FAILED)
				);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
	}

	@Test
	public void createEmptyChangeSet() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeChangeSet("bar"))
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
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=0", run);

	}

	@Test
	public void createEmptyChangeSet_statusReason() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeChangeSet("bar"))
				.thenReturn(new DescribeChangeSetResult()
						.withStatus(ChangeSetStatus.FAILED)
						.withStatusReason("No updates are to be performed.")
				);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=0", run);

	}

	@Test
	public void createChangeSetStackDoesNotExist() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(false);
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
				.withStatus(ChangeSetStatus.CREATE_COMPLETE)
		);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')\n"
				+ "  echo \"changesCount=${changes.size()}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), nullable(String.class),
				nullable(String.class), Mockito.anyCollection(), Mockito.anyCollection(),
				Mockito.anyCollection(), Mockito.any(PollConfiguration.class), Mockito.eq(ChangeSetType.CREATE), nullable(String.class), Mockito.any());
	}

}
