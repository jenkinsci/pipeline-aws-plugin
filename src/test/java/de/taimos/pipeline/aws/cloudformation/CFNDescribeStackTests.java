package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CFNDescribeStackTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private CloudFormationStack stack;
	private AmazonCloudFormation cloudFormation;
	private int stackCounter;

	@Before
	public void setupSdk() throws Exception {
		this.stack = Mockito.mock(CloudFormationStack.class);
		this.cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.cloudFormation);
		AWSUtilFactory.setStackSupplier(s -> {
			assertEquals("foo", s);
			stackCounter++;
			return stack;
		});
		stackCounter = 0;
	}

	@Test
	public void describe() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.stack.describeOutputs()).thenReturn(Collections.singletonMap("foo", "bar"));
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  def outputs = cfnDescribe(stack: 'foo')\n"
														+ "  echo \"foo=${outputs['foo']}\""
														+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("foo=bar", run);

		assertTrue(stackCounter > 0);
	}

}
