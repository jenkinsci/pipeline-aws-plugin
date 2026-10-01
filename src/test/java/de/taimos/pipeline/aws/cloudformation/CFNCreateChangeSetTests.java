package de.taimos.pipeline.aws.cloudformation;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class CFNCreateChangeSetTests {

	private JenkinsRule jenkinsRule;
	private CloudFormationStack stack;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.stack = mock(CloudFormationStack.class);
		CloudFormationClient cloudFormation = mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate(x -> cloudFormation);
		AWSUtilFactory.setStackSupplier((s) -> {
			assertEquals("foo", s);
			return stack;
		});
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
		AWSUtilFactory.setStackSupplier(null);
	}

	@Test
	void createChangeSetStackParametersFromMap() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(true);
		when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
		);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', params: ['foo': 'bar', 'baz': 'true'])
                  echo "changesCount=${changes.size()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		verify(this.stack).createChangeSet(eq("bar"),
				nullable(String.class), nullable(String.class), eq(Arrays.asList(
				Parameter.builder().parameterKey("foo").parameterValue("bar").build(),
				Parameter.builder().parameterKey("baz").parameterValue("true").build()
		)), anyCollection(), anyCollection(), any(PollConfiguration.class), eq(ChangeSetType.UPDATE), nullable(String.class),
												   any());
	}

	@Test
	void createChangeSetStackExists() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(true);
		when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
		);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')
                  echo "changesCount=${changes.size()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		verify(this.stack).createChangeSet(eq("bar"), nullable(String.class), nullable(String.class),
				anyCollection(), anyCollection(), anyCollection(),
				any(PollConfiguration.class), eq(ChangeSetType.UPDATE), nullable(String.class), any());
	}

	@Test
	void createChangeSetWithRawTemplate() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(true);
		when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
		);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', template: 'foobaz')
                  echo "changesCount=${changes.size()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		verify(this.stack).createChangeSet(eq("bar"), eq("foobaz"), nullable(String.class),
				anyCollection(), anyCollection(), anyCollection(),
				any(PollConfiguration.class), eq(ChangeSetType.UPDATE), nullable(String.class), any());
	}

	@Test
	void updateChangeSetWithRawTemplate() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(false);
		when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
		);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar', template: 'foobaz')
                  echo "changesCount=${changes.size()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		verify(this.stack).createChangeSet(eq("bar"), eq("foobaz"), nullable(String.class),
				anyCollection(), anyCollection(), anyCollection(),
				any(PollConfiguration.class), eq(ChangeSetType.CREATE), nullable(String.class), any());
	}

	@Test
	void createChangeSetStackFailure() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(true);
		when(this.stack.describeChangeSet("bar"))
				.thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).build()
				);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
	}

	@Test
	void createEmptyChangeSet() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(true);
		when(this.stack.describeChangeSet("bar"))
				.thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("The submitted information didn't contain changes. Submit different information to create a change set.").build()
				);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')
                  echo "changesCount=${changes.size()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=0", run);

	}

	@Test
	void createEmptyChangeSet_statusReason() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(true);
		when(this.stack.describeChangeSet("bar"))
				.thenReturn(DescribeChangeSetResponse.builder().status(ChangeSetStatus.FAILED).statusReason("No updates are to be performed.").build()
				);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')
                  echo "changesCount=${changes.size()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=0", run);

	}

	@Test
	void createChangeSetStackDoesNotExist() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(false);
		when(this.stack.describeChangeSet("bar")).thenReturn(DescribeChangeSetResponse.builder().changes(Change.builder().build()).status(ChangeSetStatus.CREATE_COMPLETE).build()
		);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def changes = cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')
                  echo "changesCount=${changes.size()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("changesCount=1", run);

		verify(this.stack).createChangeSet(eq("bar"), nullable(String.class),
				nullable(String.class), anyCollection(), anyCollection(),
				anyCollection(), any(PollConfiguration.class), eq(ChangeSetType.CREATE), nullable(String.class), any());
	}

}
