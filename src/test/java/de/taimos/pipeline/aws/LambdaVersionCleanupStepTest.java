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
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesResult;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionRequest;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;

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
	private AmazonCloudFormation cloudformation;

	@Before
	public void setupSdk() throws Exception {
		PowerMockito.mockStatic(AWSClientFactory.class);
		this.awsLambda = Mockito.mock(AWSLambda.class);
		this.cloudformation = Mockito.mock(AmazonCloudFormation.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(StepContext.class)))
			.thenAnswer( (x) -> {
				if (x.getArgumentAt(0, AwsSyncClientBuilder.class) instanceof AWSLambdaClientBuilder) {
					return awsLambda;
				} else {
					return cloudformation;
				}
			});
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

	@Test
	public void deleteCloudFormationStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(this.awsLambda.listVersionsByFunction(Mockito.eq(new ListVersionsByFunctionRequest().withFunctionName("foo")))).thenReturn(new ListVersionsByFunctionResult()
						.withVersions(Arrays.asList(
								new FunctionConfiguration().withVersion("v1").withLastModified(ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)),
								new FunctionConfiguration().withVersion("v2").withLastModified("2018-02-05T11:15:12Z")
						))
		);
		Mockito.when(this.cloudformation.describeStackResources(new DescribeStackResourcesRequest().withStackName("baz"))).thenReturn(new DescribeStackResourcesResult().withStackResources(
					new StackResource()
						.withResourceType("AWS::Lambda::Function")
						.withPhysicalResourceId("foo"),
					new StackResource()
						.withResourceType("AWS::Baz::Function")
						.withPhysicalResourceId("bar")
					)
		);
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  lambdaVersionCleanup(stackName: 'baz', daysAgo: 5)\n"
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
