package de.taimos.pipeline.aws.ecr;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.BatchDeleteImageRequest;
import software.amazon.awssdk.services.ecr.model.BatchDeleteImageResponse;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import de.taimos.pipeline.aws.AWSClientFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import hudson.model.Run;
import org.junit.After;
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
	private EcrClient ecr;

	@Before
	public void setupSdk() throws Exception {
		this.ecr = Mockito.mock(EcrClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.ecr);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void stubDeletedImage() {
		Mockito.when(this.ecr.batchDeleteImage(Mockito.any(BatchDeleteImageRequest.class)))
				.thenReturn(BatchDeleteImageResponse.builder()
						.imageIds(ImageIdentifier.builder().imageTag("it1").imageDigest("id1").build())
						.failures(Collections.emptyList())
						.build()
				);
	}

	@Test
	public void deleteImage() throws Exception {
		this.stubDeletedImage();
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
		Assert.assertEquals(BatchDeleteImageRequest.builder()
				.imageIds(
						ImageIdentifier.builder().imageTag("it1").imageDigest("id1").build()
				)
				.registryId("rId")
				.repositoryName("rName")
				.build(), request);
	}

	/**
	 * The step returns maps rather than SDK model objects, because script-security rejects field
	 * access on the model in either SDK - so this is the first form of the result a pipeline can
	 * actually read.
	 */
	@Test
	public void returnsImageIdsAsReadableMaps() throws Exception {
		this.stubDeletedImage();
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ecrDeleteReturn");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def r = ecrDeleteImage(imageIds: [[imageTag: 'it1']], repositoryName: 'rName')\n"
				+ "  echo \"tag=${r[0].imageTag} digest=${r[0].imageDigest}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("tag=it1 digest=id1", run);
	}

	/**
	 * registryId is optional - it defaults to the caller's registry - so the step has to work
	 * without it.
	 */
	@Test
	public void worksWithoutARegistryId() throws Exception {
		Mockito.when(this.ecr.batchDeleteImage(Mockito.any(BatchDeleteImageRequest.class)))
				.thenReturn(BatchDeleteImageResponse.builder().failures(Collections.emptyList()).build());
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ecrDeleteNoRegistry");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  ecrDeleteImage(imageIds: [[imageTag: 'it1']], repositoryName: 'rName')\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<BatchDeleteImageRequest> captor = ArgumentCaptor.forClass(BatchDeleteImageRequest.class);
		Mockito.verify(this.ecr).batchDeleteImage(captor.capture());
		Assert.assertNull(captor.getValue().registryId());
	}

}
