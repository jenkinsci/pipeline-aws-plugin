package de.taimos.pipeline.aws;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.LaunchPermission;
import software.amazon.awssdk.services.ec2.model.LaunchPermissionModifications;
import software.amazon.awssdk.services.ec2.model.ModifyImageAttributeRequest;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@WithJenkins
class EC2ShareAmiStepTests {

	private JenkinsRule jenkinsRule;
	private Ec2Client ec2;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.ec2 = mock(Ec2Client.class);
		AWSClientFactory.setFactoryDelegate(x -> this.ec2);
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	@Test
	void validateModifyAttributeRequest() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ec2Test");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  ec2ShareAmi(amiId: 'foo', accountIds: ['a1', 'a2'])
                }
                """, true)
		);

		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<ModifyImageAttributeRequest> captor = ArgumentCaptor.forClass(ModifyImageAttributeRequest.class);
		verify(this.ec2).modifyImageAttribute(captor.capture());
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
