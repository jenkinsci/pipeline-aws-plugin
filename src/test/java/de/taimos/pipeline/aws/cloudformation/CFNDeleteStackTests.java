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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CFNDeleteStackTests {

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
	public void deleteStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  cfnDelete(stack: 'foo', pollInterval: 25, timeoutInMinutes: 17, roleArn: 'myarn', clientRequestToken: 'myrequesttoken')"
														+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		PollConfiguration pollConfiguration = PollConfiguration.builder().pollInterval(Duration.ofMillis(25)).timeout(Duration.ofMinutes(17)).build();
		Mockito.verify(this.stack).delete(Mockito.eq(pollConfiguration), Mockito.any(), Mockito.eq("myarn"), Mockito.eq("myrequesttoken"));
	}

}
