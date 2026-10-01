package de.taimos.pipeline.aws.cloudformation;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.paginators.ListExportsIterable;
import software.amazon.awssdk.services.cloudformation.model.Export;
import software.amazon.awssdk.services.cloudformation.model.ListExportsRequest;
import software.amazon.awssdk.services.cloudformation.model.ListExportsResponse;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@WithJenkins
class CFNExportsStepTests {

	private JenkinsRule jenkinsRule;
	private CloudFormationClient cloudFormation;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.cloudFormation = mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate(x -> this.cloudFormation);
		// the step pages through exports; a real paginator over the mock keeps the listExports
		// stubs below meaningful
		when(this.cloudFormation.listExportsPaginator(any(ListExportsRequest.class)))
				.thenAnswer(invocation -> new ListExportsIterable(this.cloudFormation, invocation.getArgument(0)));
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	@Test
	void listExports() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		// Answers on the request's token rather than on an exact request instance: the paginator
		// builds each page's request itself.
		when(this.cloudFormation.listExports(any(ListExportsRequest.class)))
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
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def exports = cfnExports()
                  echo "exportsCount=${exports.size()}"
                  echo "foo=${exports['foo']}"
                  echo "baz=${exports['baz']}"
                }
                """, true)
		);

		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("exportsCount=2", run);
		this.jenkinsRule.assertLogContains("foo=bar", run);
		this.jenkinsRule.assertLogContains("baz=foo", run);
	}

}
