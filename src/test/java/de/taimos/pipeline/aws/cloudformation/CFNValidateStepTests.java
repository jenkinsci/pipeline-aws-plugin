package de.taimos.pipeline.aws.cloudformation;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import software.amazon.awssdk.services.cloudformation.model.ValidateTemplateRequest;
import software.amazon.awssdk.services.cloudformation.model.ValidateTemplateResponse;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Result;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class CFNValidateStepTests {

	private JenkinsRule jenkinsRule;
	private CloudFormationClient cloudFormation;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.cloudFormation = mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate(x -> this.cloudFormation);
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	@Test
	void validateWithUrlSuccess() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def response = cfnValidate(url: 'foo')
                  echo "description=${response.description}"
                  echo "parameters=${response.parameters.toString()}"
                }
                """, true)
		);
		when(this.cloudFormation.validateTemplate(any(ValidateTemplateRequest.class))).thenReturn(ValidateTemplateResponse.builder()
				.description("myDescription")
				.parameters(TemplateParameter.builder()
						.defaultValue("hello")
						.description("myParamDescription")
						.parameterKey("myParam").build()
				).build()
		);

		WorkflowRun run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkinsRule.assertLogContains("description=myDescription", run);
		jenkinsRule.assertLogContains("parameters=[[parameterKey:myParam, defaultValue:hello, noEcho:null, description:myParamDescription]]", run);
		ArgumentCaptor<ValidateTemplateRequest> captor = ArgumentCaptor.forClass(ValidateTemplateRequest.class);
		verify(this.cloudFormation).validateTemplate(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(ValidateTemplateRequest.builder()
				.templateURL("foo").build()
		);
	}

	@Test
	void validateWithUrlFailure() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		CloudFormationException ex = (CloudFormationException) CloudFormationException.builder().message("invalid template").awsErrorDetails(AwsErrorDetails.builder().errorMessage("invalid template").build()).build();
		when(this.cloudFormation.validateTemplate(any(ValidateTemplateRequest.class)))
				.thenThrow(ex);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnValidate(url: 'foo')
                }
                """, true)
		);

		this.jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));

		ArgumentCaptor<ValidateTemplateRequest> captor = ArgumentCaptor.forClass(ValidateTemplateRequest.class);
		verify(this.cloudFormation).validateTemplate(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(ValidateTemplateRequest.builder()
				.templateURL("foo").build()
		);
	}

}
