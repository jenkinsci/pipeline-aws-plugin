package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.nullable;

public class CFNUpdateStackTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private CloudFormationStack stack;
	private AmazonCloudFormation cloudFormation;

	@Before
	public void setupSdk() throws Exception {
		this.stack = Mockito.mock(CloudFormationStack.class);
		this.cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.cloudFormation);
		AWSUtilFactory.setStackSupplier(s -> {
			assertEquals("foo", s);
			return stack;
		});
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

		Mockito.verify(this.stack).create(nullable(String.class), nullable(String.class), Mockito.anyCollection(),
				Mockito.anyCollection(), Mockito.anyCollection(), Mockito.any(), nullable(String.class), Mockito.anyString(), nullable(Boolean.class));
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

		Mockito.verify(this.stack).update(nullable(String.class), nullable(String.class), Mockito.anyCollection(),
				Mockito.anyCollection(), Mockito.anyCollection(), Mockito.any(), nullable(String.class), Mockito.any());
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

		Mockito.verify(this.stack, Mockito.never()).create(Mockito.anyString(), Mockito.anyString(), Mockito.anyCollection(), Mockito.anyCollection(), Mockito.anyCollection(), Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean());
	}
}
