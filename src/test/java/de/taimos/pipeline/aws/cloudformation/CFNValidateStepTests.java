package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.TemplateParameter;
import com.amazonaws.services.cloudformation.model.ValidateTemplateRequest;
import com.amazonaws.services.cloudformation.model.ValidateTemplateResult;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Result;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
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
public class CFNValidateStepTests {

	@Rule
	private JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonCloudFormation cloudFormation;

	@Before
	public void setupSdk() throws Exception {
		PowerMockito.mockStatic(AWSClientFactory.class);
		this.cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(StepContext.class)))
				.thenReturn(this.cloudFormation);
	}

	@Test
	public void validateWithUrlSuccess() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def response = cfnValidate(url: 'foo')\n"
				+ "  echo \"description=${response.description}\"\n"
				+ "  echo \"parameters=${response.parameters.toString()}\"\n"
				+ "}\n", true)
		);
		Mockito.when(this.cloudFormation.validateTemplate(Mockito.any())).thenReturn(new ValidateTemplateResult()
				.withDescription("myDescription")
				.withParameters(new TemplateParameter()
						.withDefaultValue("hello")
						.withDescription("myParamDescription")
						.withParameterKey("myParam")
				)
		);

		WorkflowRun run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkinsRule.assertLogContains("description=myDescription", run);
		jenkinsRule.assertLogContains("parameters=[[parameterKey:myParam, defaultValue:hello, noEcho:null, description:myParamDescription]]", run);
		ArgumentCaptor<ValidateTemplateRequest> captor = ArgumentCaptor.forClass(ValidateTemplateRequest.class);
		Mockito.verify(this.cloudFormation).validateTemplate(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new ValidateTemplateRequest()
				.withTemplateURL("foo")
		);
	}

	@Test
	public void validateWithUrlFailure() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		AmazonCloudFormationException ex = new AmazonCloudFormationException("invalid template");
		Mockito.when(this.cloudFormation.validateTemplate(Mockito.any(ValidateTemplateRequest.class)))
				.thenThrow(ex);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfnValidate(url: 'foo')"
				+ "}\n", true)
		);

		this.jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));

		ArgumentCaptor<ValidateTemplateRequest> captor = ArgumentCaptor.forClass(ValidateTemplateRequest.class);
		Mockito.verify(this.cloudFormation).validateTemplate(captor.capture());
		Assertions.assertThat(captor.getValue()).isEqualTo(new ValidateTemplateRequest()
				.withTemplateURL("foo")
		);
	}

}
