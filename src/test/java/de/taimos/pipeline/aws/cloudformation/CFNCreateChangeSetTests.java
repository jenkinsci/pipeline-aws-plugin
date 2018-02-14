package de.taimos.pipeline.aws.cloudformation;

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

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.model.TaskListener;

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
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')"
														+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		PowerMockito.verifyNew(CloudFormationStack.class, Mockito.atLeastOnce()).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection(), Mockito.anyInt(), Mockito.eq(ChangeSetType.UPDATE), Mockito.anyString());
	}

	@Test
	public void createChangeSetStackDoesNotExist() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(false);
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  cfnCreateChangeSet(stack: 'foo', changeSet: 'bar')"
														+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		PowerMockito.verifyNew(CloudFormationStack.class, Mockito.atLeastOnce()).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(this.stack).createChangeSet(Mockito.eq("bar"), Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection(), Mockito.anyInt(), Mockito.eq(ChangeSetType.CREATE), Mockito.anyString());
	}

}
