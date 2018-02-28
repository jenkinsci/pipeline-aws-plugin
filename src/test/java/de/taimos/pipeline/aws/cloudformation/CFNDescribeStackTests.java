package de.taimos.pipeline.aws.cloudformation;

import java.util.Collections;

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

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
		value = AWSClientFactory.class,
		fullyQualifiedNames = "de.taimos.pipeline.aws.cloudformation.*"
)
@PowerMockIgnore("javax.crypto.*")
public class CFNDescribeStackTests {

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
	public void describe() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeOutputs()).thenReturn(Collections.singletonMap("foo", "bar"));
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  def outputs = cfnDescribe(stack: 'foo')\n"
														+ "  echo \"foo=${outputs['foo']}\""
														+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("foo=bar", run);

		PowerMockito.verifyNew(CloudFormationStack.class).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
	}

	@Test
	public void describeNonExistantStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(false);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def outputs = cfnDescribe(stack: 'foo')\n"
				+ "  echo \"outputs=${outputs}\""
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("outputs=null", run);

		PowerMockito.verifyNew(CloudFormationStack.class).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
	}

}
