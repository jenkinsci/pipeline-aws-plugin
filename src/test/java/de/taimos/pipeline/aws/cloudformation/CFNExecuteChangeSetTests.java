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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CFNExecuteChangeSetTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private CloudFormationStack stack;

	@Before
	public void setupSdk() throws Exception {
		this.stack = Mockito.mock(CloudFormationStack.class);
		AmazonCloudFormation cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		AWSClientFactory.setFactoryDelegate((x) -> cloudFormation);
		AWSUtilFactory.setStackSupplier(s -> {
			assertEquals("foo", s);
			return stack;
		});
	}

	@Test
	public void executeChangeSet() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  cfnExecuteChangeSet(stack: 'foo', changeSet: 'bar')"
														+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(this.stack).executeChangeSet(Mockito.eq("bar"), Mockito.any(PollConfiguration.class));
	}

}
