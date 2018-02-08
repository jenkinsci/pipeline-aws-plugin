package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.*;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
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
	private AmazonCloudFormation cloudFormation;

	@Before
	public void setupSdk() throws Exception {
		this.stack = Mockito.mock(CloudFormationStack.class);
		PowerMockito.mockStatic(AWSClientFactory.class);
		PowerMockito.whenNew(CloudFormationStack.class)
				.withAnyArguments()
				.thenReturn(this.stack);
		this.cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(EnvVars.class)))
				.thenReturn(this.cloudFormation);
	}

	@Test
	public void createChangeSetStackExists() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(true);
		Mockito.when(stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
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
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection(), Mockito.anyInt(), Mockito.eq(ChangeSetType.UPDATE), Mockito.anyString());
	}

	@Test
	public void createChangeSetStackDoesNotExist() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stack.exists()).thenReturn(false);
		Mockito.when(stack.describeChangeSet("bar")).thenReturn(new DescribeChangeSetResult()
				.withChanges(new Change())
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
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection(), Mockito.anyInt(), Mockito.eq(ChangeSetType.CREATE), Mockito.anyString());
	}

}
