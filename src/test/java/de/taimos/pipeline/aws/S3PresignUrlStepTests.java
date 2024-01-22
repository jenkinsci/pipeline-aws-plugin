package de.taimos.pipeline.aws;


import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.URL;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;

public class S3PresignUrlStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonS3 s3;

	@Before
	public void setupSdk() throws Exception {
		this.s3 = Mockito.mock(AmazonS3.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.s3);
	}

	@Test
	public void presignWithDefaultExpiration() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3PresignTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def url = s3PresignURL(bucket: 'foo', key: 'bar')\n"
				+ "  echo \"url=$url\"\n"
				+ "}\n", true)
		);
		//defaults to 1 minute
		//minus a buffer for the test
		Date expectedDate = DateTime.now().plusMinutes(1).minusSeconds(10).toDate();

		String urlString = "http://localhost:283/sdkd";
		URL url = new URL(urlString);
		Mockito.when(this.s3.generatePresignedUrl(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(url);

		WorkflowRun run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkinsRule.assertLogContains("url=" + urlString, run);
		ArgumentCaptor<Date> expirationCaptor = ArgumentCaptor.forClass(Date.class);
		Mockito.verify(s3).generatePresignedUrl(Mockito.eq("foo"), Mockito.eq("bar"), expirationCaptor.capture(), Mockito.eq(HttpMethod.GET));

		assertThat(expirationCaptor.getValue(), greaterThanOrEqualTo(expectedDate));
	}

	@Test
	public void presignWithCustomMethod() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3PresignTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def url = s3PresignURL(bucket: 'foo', key: 'bar', httpMethod: 'POST')\n"
				+ "  echo \"url=$url\"\n"
				+ "}\n", true)
		);

		String urlString = "http://localhost:283/sdkd";
		URL url = new URL(urlString);
		Mockito.when(this.s3.generatePresignedUrl(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(url);

		WorkflowRun run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkinsRule.assertLogContains("url=" + urlString, run);
		ArgumentCaptor<Date> expirationCaptor = ArgumentCaptor.forClass(Date.class);
		Mockito.verify(s3).generatePresignedUrl(Mockito.eq("foo"), Mockito.eq("bar"), Mockito.any(), Mockito.eq(HttpMethod.POST));
	}

	@Test
	public void presignWithExpiration() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "s3PresignTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def url = s3PresignURL(bucket: 'foo', key: 'bar', durationInSeconds: 10000)\n"
				+ "  echo \"url=$url\"\n"
				+ "}\n", true)
		);
		String urlString = "http://localhost:283/sdkd";
		URL url = new URL(urlString);
		Mockito.when(this.s3.generatePresignedUrl(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(url);

		//minus a buffer for the test
		Date expectedDate = DateTime.now().plusSeconds(10000).minusSeconds(25).toDate();
		WorkflowRun run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkinsRule.assertLogContains("url=" + urlString, run);
		ArgumentCaptor<Date> expirationCaptor = ArgumentCaptor.forClass(Date.class);
		Mockito.verify(s3).generatePresignedUrl(Mockito.eq("foo"), Mockito.eq("bar"), expirationCaptor.capture(), Mockito.eq(HttpMethod.GET));

		assertThat(expirationCaptor.getValue(), greaterThanOrEqualTo(expectedDate));
	}
}
