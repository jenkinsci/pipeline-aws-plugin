package de.taimos.pipeline.aws;

import java.util.Collections;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionRequest;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionConfiguration;

import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import com.amazonaws.services.lambda.AWSLambda;

import java.util.Arrays;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AWSClientFactory.class)
@PowerMockIgnore("javax.crypto.*")
public class LambdaVersionCleanupStepTest {

	@Rule
	private JenkinsRule jenkinsRule = new JenkinsRule();
	private AWSLambda awsLambda;

	@Before
	public void setupSdk() throws Exception {
		PowerMockito.mockStatic(AWSClientFactory.class);
		this.awsLambda = Mockito.mock(AWSLambda.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(StepContext.class)))
				.thenReturn(this.awsLambda);
	}

	@Test
	public void deleteSingleFunction() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.awsLambda.listVersionsByFunction(Mockito.eq(new ListVersionsByFunctionRequest().withFunctionName("foo")))).thenReturn(new ListVersionsByFunctionResult()
						.withVersions(Arrays.asList(
								new FunctionConfiguration().withVersion("v1").withLastModified(ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)),
								new FunctionConfiguration().withVersion("v2").withLastModified("2018-02-05T11:15:12Z")
						))
		);
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  lambdaVersionCleanup(functionName: 'foo', daysAgo: 5)\n"
														+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		Mockito.verify(this.awsLambda).deleteFunction(new DeleteFunctionRequest()
			.withQualifier("v2")
			.withFunctionName("foo")
		);
		Mockito.verify(this.awsLambda).listVersionsByFunction(Mockito.any());
		Mockito.verifyNoMoreInteractions(this.awsLambda);
	}

}
