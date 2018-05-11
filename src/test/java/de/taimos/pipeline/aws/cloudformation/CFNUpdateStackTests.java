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
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
		value = AWSClientFactory.class,
		fullyQualifiedNames = "de.taimos.pipeline.aws.cloudformation.*"
)
@PowerMockIgnore("javax.crypto.*")
public class CFNUpdateStackTests {

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
	public void createNonExistantStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
		Mockito.when(this.stack.exists()).thenReturn(false);
		Mockito.when(this.stack.describeOutputs()).thenReturn(Collections.singletonMap("foo", "bar"));
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  cfnUpdate(stack: 'foo')"
														+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(this.stack).create(Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean());
	}

	@Test
	public void updateExistantStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(true);
		Mockito.when(this.stack.describeOutputs()).thenReturn(Collections.singletonMap("foo", "bar"));
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  cfnUpdate(stack: 'foo')"
														+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(this.stack).update(Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection(), Mockito.anyLong(), Mockito.anyString(), Mockito.any());
	}

	@Test
	public void doNotCreateNonExistantStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.exists()).thenReturn(false);
		Mockito.when(this.stack.describeOutputs()).thenReturn(Collections.singletonMap("foo", "bar"));
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  cfnUpdate(stack: 'foo', create: false)"
														+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(this.stack, Mockito.never()).create(Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection(), Mockito.anyInt(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean());
	}
}
