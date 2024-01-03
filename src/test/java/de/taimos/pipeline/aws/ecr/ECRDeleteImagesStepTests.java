package de.taimos.pipeline.aws.ecr;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.BatchDeleteImageRequest;
import com.amazonaws.services.ecr.model.BatchDeleteImageResult;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import de.taimos.pipeline.aws.AWSClientFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;

public class ECRDeleteImagesStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonECR ecr;

	@Before
	public void setupSdk() throws Exception {
		this.ecr = Mockito.mock(AmazonECR.class);
		AWSClientFactory.setFactoryDelegate((x) -> ecr);
	}

	@Test
	public void deleteImage() throws Exception {
		Mockito.when(ecr.batchDeleteImage(Mockito.any())).thenReturn(new BatchDeleteImageResult()
				.withFailures(Collections.emptyList())
		);
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  ecrDeleteImage(imageIds: [[imageTag: 'it1', imageDigest: 'id1']], registryId: 'rId', repositoryName: 'rName')\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<BatchDeleteImageRequest> argumentCaptor = ArgumentCaptor.forClass(BatchDeleteImageRequest.class);
		Mockito.verify(this.ecr).batchDeleteImage(argumentCaptor.capture());

		BatchDeleteImageRequest request = argumentCaptor.getValue();
		Assert.assertEquals(new BatchDeleteImageRequest()
				.withImageIds(
						new ImageIdentifier().withImageTag("it1").withImageDigest("id1")
				)
				.withRegistryId("rId")
				.withRepositoryName("rName"), request);
	}

}
