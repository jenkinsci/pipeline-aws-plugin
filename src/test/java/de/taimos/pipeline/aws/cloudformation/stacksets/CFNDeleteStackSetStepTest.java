package de.taimos.pipeline.aws.cloudformation.stacksets;

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

public class CFNDeleteStackSetStepTest {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private CloudFormationStackSet stackSet;

	@Before
	public void setupSdk() throws Exception {
		stackSet = Mockito.mock(CloudFormationStackSet.class);
		AmazonCloudFormation cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		AWSClientFactory.setFactoryDelegate((x) -> cloudFormation);
		AWSUtilFactory.setStackSetSupplier(s -> {
			assertEquals("foo", s);
			return stackSet;
		});
	}

	@Test
	public void deleteStackSet() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfnDeleteStackSet(stackSet: 'foo')"
				+ "}\n", true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		Mockito.verify(stackSet).delete();
	}
}
