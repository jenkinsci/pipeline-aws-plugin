package de.taimos.pipeline.aws;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.ListStackResourcesRequest;
import software.amazon.awssdk.services.cloudformation.model.ListStackResourcesResponse;
import software.amazon.awssdk.services.cloudformation.model.StackResourceSummary;
import software.amazon.awssdk.services.cloudformation.paginators.ListStackResourcesIterable;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;
import software.amazon.awssdk.services.lambda.model.AliasConfiguration;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.ListAliasesRequest;
import software.amazon.awssdk.services.lambda.model.ListAliasesResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.paginators.ListVersionsByFunctionIterable;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class LambdaVersionCleanupStepTest {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private LambdaClient awsLambda;
	private CloudFormationClient cloudformation;

	@Before
	public void setupSdk() throws Exception {
		this.awsLambda = Mockito.mock(LambdaClient.class);
		this.cloudformation = Mockito.mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> {
			if (x instanceof LambdaClientBuilder) {
				return this.awsLambda;
			} else {
				return this.cloudformation;
			}
		});
		// The step drives the SDK paginators, which are default methods on the client interface and
		// so return null from a mock. Handing back real paginators over the mock keeps the
		// underlying listVersionsByFunction/listStackResources stubs and verifications meaningful.
		Mockito.when(this.awsLambda.listVersionsByFunctionPaginator(Mockito.any(ListVersionsByFunctionRequest.class)))
				.thenAnswer(invocation -> new ListVersionsByFunctionIterable(this.awsLambda, invocation.getArgument(0)));
		Mockito.when(this.cloudformation.listStackResourcesPaginator(Mockito.any(ListStackResourcesRequest.class)))
				.thenAnswer(invocation -> new ListStackResourcesIterable(this.cloudformation, invocation.getArgument(0)));
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private static String recently() {
		return ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
	}

	private static String daysAgo(int days) {
		return ZonedDateTime.now().minusDays(days).format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
	}

	private void stubNoAliases(String functionName) {
		Mockito.when(this.awsLambda.listAliases(ListAliasesRequest.builder().functionName(functionName).build()))
				.thenReturn(ListAliasesResponse.builder().build());
	}

	/**
	 * Answers on the request's contents rather than on an exact request instance: the paginator
	 * builds each page's request itself, so matching the whole object couples the test to how the
	 * SDK carries the marker between pages.
	 */
	private void stubVersions(String functionName, FunctionConfiguration... versions) {
		Mockito.when(this.awsLambda.listVersionsByFunction(Mockito.any(ListVersionsByFunctionRequest.class)))
				.thenAnswer(invocation -> {
					ListVersionsByFunctionRequest request = invocation.getArgument(0);
					if (!functionName.equals(request.functionName())) {
						return ListVersionsByFunctionResponse.builder().build();
					}
					return ListVersionsByFunctionResponse.builder().versions(Arrays.asList(versions)).build();
				});
	}

	private static FunctionConfiguration version(String version, String lastModified) {
		return FunctionConfiguration.builder().version(version).lastModified(lastModified).build();
	}

	private void runCleanup(String jobName, String args) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  lambdaVersionCleanup(" + args + ")\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	public void deleteSingleFunction() throws Exception {
		this.stubNoAliases("foo");
		this.stubVersions("foo", version("v1", recently()), version("v2", "2018-02-05T11:15:12Z"));

		this.runCleanup("cfnTest", "functionName: 'foo', daysAgo: 5");

		Mockito.verify(this.awsLambda).deleteFunction(DeleteFunctionRequest.builder()
				.qualifier("v2")
				.functionName("foo")
				.build()
		);
		// The point of the step: v1 is newer than the cutoff and must be left alone.
		Mockito.verify(this.awsLambda, Mockito.never()).deleteFunction(DeleteFunctionRequest.builder()
				.qualifier("v1")
				.functionName("foo")
				.build()
		);
	}

	@Test
	public void paginatedResponse() throws Exception {
		this.stubNoAliases("foo");
		Mockito.when(this.awsLambda.listVersionsByFunction(Mockito.any(ListVersionsByFunctionRequest.class)))
				.thenAnswer(invocation -> {
					ListVersionsByFunctionRequest request = invocation.getArgument(0);
					if (request.marker() == null) {
						return ListVersionsByFunctionResponse.builder()
								.versions(Arrays.asList(version("v1", recently())))
								.nextMarker("baz")
								.build();
					}
					return ListVersionsByFunctionResponse.builder()
							.versions(Arrays.asList(version("v2", "2018-02-05T11:15:12Z")))
							.build();
				});

		this.runCleanup("cfnTest", "functionName: 'foo', daysAgo: 5");

		Mockito.verify(this.awsLambda).deleteFunction(DeleteFunctionRequest.builder()
				.qualifier("v2")
				.functionName("foo")
				.build()
		);
		Mockito.verify(this.awsLambda, Mockito.never()).deleteFunction(DeleteFunctionRequest.builder()
				.qualifier("v1")
				.functionName("foo")
				.build()
		);
		Mockito.verify(this.awsLambda, Mockito.times(2)).listVersionsByFunction(Mockito.any(ListVersionsByFunctionRequest.class));
	}

	@Test
	public void ignoreLatest() throws Exception {
		this.stubNoAliases("foo");
		this.stubVersions("foo", version("$LATEST", daysAgo(15)));

		this.runCleanup("cfnTest", "functionName: 'foo', daysAgo: 5");

		Mockito.verify(this.awsLambda, Mockito.never()).deleteFunction(Mockito.any(DeleteFunctionRequest.class));
	}

	@Test
	public void ignoreAliases() throws Exception {
		Mockito.when(this.awsLambda.listAliases(ListAliasesRequest.builder().functionName("foo").build()))
				.thenReturn(ListAliasesResponse.builder()
						.aliases(AliasConfiguration.builder().functionVersion("myVersion").build())
						.build());
		this.stubVersions("foo", version("myVersion", daysAgo(15)));

		this.runCleanup("cfnTest", "functionName: 'foo', daysAgo: 5");

		Mockito.verify(this.awsLambda, Mockito.never()).deleteFunction(Mockito.any(DeleteFunctionRequest.class));
	}

	@Test
	public void deleteCloudFormationStack() throws Exception {
		this.stubNoAliases("foo");
		this.stubNoAliases("foo2");
		Mockito.when(this.awsLambda.listVersionsByFunction(Mockito.any(ListVersionsByFunctionRequest.class)))
				.thenAnswer(invocation -> ListVersionsByFunctionResponse.builder()
						.versions(Arrays.asList(version("v1", recently()), version("v2", "2018-02-05T11:15:12Z")))
						.build());

		Mockito.when(this.cloudformation.listStackResources(Mockito.any(ListStackResourcesRequest.class)))
				.thenAnswer(invocation -> {
					ListStackResourcesRequest request = invocation.getArgument(0);
					if (request.nextToken() == null) {
						return ListStackResourcesResponse.builder()
								.stackResourceSummaries(
										StackResourceSummary.builder().resourceType("AWS::Lambda::Function").physicalResourceId("foo").build(),
										StackResourceSummary.builder().resourceType("AWS::Baz::Function").physicalResourceId("bar").build())
								.nextToken("foo")
								.build();
					}
					return ListStackResourcesResponse.builder()
							.stackResourceSummaries(
									StackResourceSummary.builder().resourceType("AWS::Lambda::Function").physicalResourceId("foo2").build(),
									StackResourceSummary.builder().resourceType("AWS::Baz::Function").physicalResourceId("bar").build())
							.build();
				});

		this.runCleanup("cfnTest", "stackName: 'baz', daysAgo: 5");

		Mockito.verify(this.awsLambda).deleteFunction(DeleteFunctionRequest.builder()
				.qualifier("v2")
				.functionName("foo")
				.build()
		);
		Mockito.verify(this.awsLambda).deleteFunction(DeleteFunctionRequest.builder()
				.qualifier("v2")
				.functionName("foo2")
				.build()
		);
		// "bar" is an AWS::Baz::Function, so the resource-type filter must keep the step away from it
		// entirely - both when listing its versions and when deleting.
		Mockito.verify(this.awsLambda, Mockito.never()).deleteFunction(Mockito.<DeleteFunctionRequest>argThat(r -> "bar".equals(r.functionName())));
		Mockito.verify(this.awsLambda, Mockito.times(2)).listVersionsByFunction(Mockito.any(ListVersionsByFunctionRequest.class));
	}
}
