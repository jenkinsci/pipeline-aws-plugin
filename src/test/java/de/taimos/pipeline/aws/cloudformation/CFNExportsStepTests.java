package de.taimos.pipeline.aws.cloudformation;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.paginators.ListExportsIterable;
import software.amazon.awssdk.services.cloudformation.model.Export;
import software.amazon.awssdk.services.cloudformation.model.ListExportsRequest;
import software.amazon.awssdk.services.cloudformation.model.ListExportsResponse;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class CFNExportsStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private CloudFormationClient cloudFormation;

	@Before
	public void setupSdk() throws Exception {
		this.cloudFormation = Mockito.mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.cloudFormation);
		// the step pages through exports; a real paginator over the mock keeps the listExports
		// stubs below meaningful
		Mockito.when(this.cloudFormation.listExportsPaginator(Mockito.any(ListExportsRequest.class)))
				.thenAnswer(invocation -> new ListExportsIterable(this.cloudFormation, invocation.getArgument(0)));
	}

	@After
	public void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	@Test
	public void listExports() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		// Answers on the request's token rather than on an exact request instance: the paginator
		// builds each page's request itself.
		Mockito.when(this.cloudFormation.listExports(Mockito.any(ListExportsRequest.class)))
				.thenAnswer(invocation -> {
					ListExportsRequest request = invocation.getArgument(0);
					if (request.nextToken() == null) {
						return ListExportsResponse.builder()
								.nextToken("foo1")
								.exports(Export.builder().name("foo").value("bar").build())
								.build();
					}
					return ListExportsResponse.builder()
							.exports(Export.builder().name("baz").value("foo").build())
							.build();
				});
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  def exports = cfnExports()\n"
														+ "  echo \"exportsCount=${exports.size()}\"\n"
														+ "  echo \"foo=${exports['foo']}\"\n"
														+ "  echo \"baz=${exports['baz']}\"\n"
														+ "}\n", true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("exportsCount=2", run);
		this.jenkinsRule.assertLogContains("foo=bar", run);
		this.jenkinsRule.assertLogContains("baz=foo", run);
	}

}
