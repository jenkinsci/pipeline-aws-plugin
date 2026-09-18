package de.taimos.pipeline.aws.cloudformation;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Change;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetStatus;
import software.amazon.awssdk.services.cloudformation.model.ChangeSetType;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetResponse;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import hudson.model.Result;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
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
		CloudFormationClient cloudFormation = Mockito.mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> cloudFormation);
		AWSUtilFactory.setStackSupplier((s) -> {
			assertEquals("foo", s);
			return stack;
		});
	}

	@After
	public void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
		AWSUtilFactory.setStackSupplier(null);
	}

	@Test
	public void createChangeSetStackParametersFromMap() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
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
				Parameter.builder().parameterKey("foo").parameterValue("bar").build(),
				Parameter.builder().parameterKey("baz").parameterValue("true").build()
		)), Mockito.anyCollection(), Mockito.anyCollection(), Mockito.any(PollConfiguration.class), Mockito.eq(ChangeSetType.UPDATE), nullable(String.class),
												   Mockito.any());
	}

	@Test
	public void createChangeSetStackExists() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
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
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
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
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
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
				.thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).build()
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
				.thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("The submitted information didn't contain changes. Submit different information to create a change set.").build()
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
				.thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("No updates are to be performed.").build()
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
		Mockito.when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
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
