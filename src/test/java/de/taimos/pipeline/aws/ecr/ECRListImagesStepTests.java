package de.taimos.pipeline.aws.ecr;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesResult;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class ECRListImagesStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonECR ecr;

	@Before
	public void setupSdk() throws Exception {
		this.ecr = Mockito.mock(AmazonECR.class);
		AWSClientFactory.setFactoryDelegate((x) -> ecr);
	}

	@Test
	public void listImages() throws Exception {
		Mockito.when(ecr.listImages(Mockito.any())).thenReturn(new ListImagesResult()
				.withImageIds(new ImageIdentifier().withImageDigest("id1").withImageTag("it1"))
				.withNextToken("next")
		).thenReturn(new ListImagesResult()
				.withImageIds(new ImageIdentifier().withImageDigest("id2").withImageTag("it2"))
		);
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def images = ecrListImages()\n"
				+ "  echo \"imagesCount=${images.size()}\"\n"
				+ "  echo \"images=${images.toString()}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("imagesCount=2", run);
		this.jenkinsRule.assertLogContains("images=[[imageTag:it1, imageDigest:id1], [imageTag:it2, imageDigest:id2]]", run);

		Mockito.verify(this.ecr, Mockito.times(2)).listImages(Mockito.any());
	}

}
