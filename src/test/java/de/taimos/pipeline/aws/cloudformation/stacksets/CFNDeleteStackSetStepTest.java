package de.taimos.pipeline.aws.cloudformation.stacksets;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
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
		CloudFormationClient cloudFormation = Mockito.mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> cloudFormation);
		AWSUtilFactory.setStackSetSupplier(s -> {
			assertEquals("foo", s);
			return stackSet;
		});
	}

	@After
	public void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
		AWSUtilFactory.setStackSetSupplier(null);
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

	/**
	 * pollInterval is dead for this step but still bindable, which is the only reason its accessors
	 * survive. Without this case a later cleanup could delete them and break existing pipelines
	 * without failing anything.
	 */
	@Test
	public void stillAcceptsTheDeprecatedPollInterval() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnDeleteStackSetPollInterval");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfnDeleteStackSet(stackSet: 'foo', pollInterval: 25)\n"
				+ "}\n", true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(stackSet).delete();
	}
}
