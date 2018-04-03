package de.taimos.pipeline.aws;


import com.amazonaws.HttpMethod;
import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import hudson.EnvVars;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.joda.time.DateTime;
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

import java.net.URL;
import java.util.Date;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AWSClientFactory.class)
@PowerMockIgnore("javax.crypto.*")
public class S3PresignUrlStepTests {

	@Rule
	private JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonS3 s3;

	@Before
	public void setupSdk() throws Exception {
		PowerMockito.mockStatic(AWSClientFactory.class);
		this.s3 = Mockito.mock(AmazonS3.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(EnvVars.class)))
				.thenReturn(this.s3);
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

		Assertions.assertThat(expirationCaptor.getValue()).isAfterOrEqualsTo(expectedDate);
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

		Assertions.assertThat(expirationCaptor.getValue()).isAfterOrEqualsTo(expectedDate);
	}
}
