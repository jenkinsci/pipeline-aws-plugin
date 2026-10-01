package de.taimos.pipeline.aws.cloudformation.stacksets;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@WithJenkins
class CFNDeleteStackSetStepTest {

	private JenkinsRule jenkinsRule;
	private CloudFormationStackSet stackSet;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		stackSet = mock(CloudFormationStackSet.class);
		CloudFormationClient cloudFormation = mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate(x -> cloudFormation);
		AWSUtilFactory.setStackSetSupplier(s -> {
			assertEquals("foo", s);
			return stackSet;
		});
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
		AWSUtilFactory.setStackSetSupplier(null);
	}

	@Test
	void deleteStackSet() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnDeleteStackSet(stackSet: 'foo')
                }
                """, true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		verify(stackSet).delete();
	}

	/**
	 * pollInterval is dead for this step but still bindable, which is the only reason its accessors
	 * survive. Without this case a later cleanup could delete them and break existing pipelines
	 * without failing anything.
	 */
	@Test
	void stillAcceptsTheDeprecatedPollInterval() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnDeleteStackSetPollInterval");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnDeleteStackSet(stackSet: 'foo', pollInterval: 25)
                }
                """, true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		verify(stackSet).delete();
	}
}
