package de.taimos.pipeline.aws.ecr;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesResult;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.cloudformation.CloudFormationStack;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepContext;
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

@RunWith(PowerMockRunner.class)
@PrepareForTest(
		value = AWSClientFactory.class,
		fullyQualifiedNames = "de.taimos.pipeline.aws.cloudformation.*"
)
@PowerMockIgnore("javax.crypto.*")
public class ECRListImagesStepTests {

	@Rule
	private JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonECR ecr;

	@Before
	public void setupSdk() throws Exception {
		this.ecr = Mockito.mock(AmazonECR.class);
		PowerMockito.mockStatic(AWSClientFactory.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(StepContext.class)))
				.thenReturn(ecr);
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
