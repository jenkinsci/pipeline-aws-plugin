package de.taimos.pipeline.aws;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.LaunchPermission;
import com.amazonaws.services.ec2.model.LaunchPermissionModifications;
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class EC2ShareAmiStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonEC2 ec2;

	@Before
	public void setupSdk() throws Exception {
		this.ec2 = Mockito.mock(AmazonEC2.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.ec2);
	}

	@Test
	public void validateModifyAttributeRequest() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ec2Test");
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  ec2ShareAmi(amiId: 'foo', accountIds: ['a1', 'a2'])"
														+ "}\n", true)
		);

		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<ModifyImageAttributeRequest> captor = ArgumentCaptor.forClass(ModifyImageAttributeRequest.class);
		Mockito.verify(this.ec2).modifyImageAttribute(captor.capture());
		assertThat(captor.getValue(), equalTo(new ModifyImageAttributeRequest()
				.withImageId("foo")
				.withLaunchPermission(new LaunchPermissionModifications()
						.withAdd(new LaunchPermission()
								.withUserId("a1")
						)
						.withAdd(new LaunchPermission()
								.withUserId("a2")
						)
				)
		));
	}

}
