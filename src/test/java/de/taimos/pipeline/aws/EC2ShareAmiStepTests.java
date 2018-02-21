package de.taimos.pipeline.aws;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.ValidateTemplateRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.LaunchPermission;
import com.amazonaws.services.ec2.model.LaunchPermissionModifications;
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest;
import com.amazonaws.services.ec2.model.ModifyImageAttributeResult;
import hudson.model.Result;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AWSClientFactory.class)
@PowerMockIgnore("javax.crypto.*")
public class EC2ShareAmiStepTests {

	@Rule
	private JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonEC2 ec2;

	@Before
	public void setupSdk() throws Exception {
		PowerMockito.mockStatic(AWSClientFactory.class);
		this.ec2 = Mockito.mock(AmazonEC2.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(StepContext.class)))
				.thenReturn(this.ec2);
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
		Assertions.assertThat(captor.getValue()).isEqualTo(new ModifyImageAttributeRequest()
				.withImageId("foo")
				.withLaunchPermission(new LaunchPermissionModifications()
						.withAdd(new LaunchPermission()
								.withUserId("a1")
						)
						.withAdd(new LaunchPermission()
								.withUserId("a2")
						)
				)
		);
	}

}
