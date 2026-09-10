package de.taimos.pipeline.aws;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.LaunchPermission;
import software.amazon.awssdk.services.ec2.model.LaunchPermissionModifications;
import software.amazon.awssdk.services.ec2.model.ModifyImageAttributeRequest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
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
	private Ec2Client ec2;

	@Before
	public void setupSdk() throws Exception {
		this.ec2 = Mockito.mock(Ec2Client.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.ec2);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
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
		assertThat(captor.getValue(), equalTo(ModifyImageAttributeRequest.builder()
				.imageId("foo")
				.launchPermission(LaunchPermissionModifications.builder()
						.add(
								LaunchPermission.builder().userId("a1").build(),
								LaunchPermission.builder().userId("a2").build()
						)
						.build()
				)
				.build()
		));
	}

}
