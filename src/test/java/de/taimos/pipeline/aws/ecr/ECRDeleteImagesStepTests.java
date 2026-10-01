package de.taimos.pipeline.aws.ecr;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.BatchDeleteImageRequest;
import software.amazon.awssdk.services.ecr.model.BatchDeleteImageResponse;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import de.taimos.pipeline.aws.AWSClientFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import hudson.model.Run;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class ECRDeleteImagesStepTests {

	private JenkinsRule jenkinsRule;
	private EcrClient ecr;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.ecr = mock(EcrClient.class);
		AWSClientFactory.setFactoryDelegate(x -> this.ecr);
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void stubDeletedImage() {
		when(this.ecr.batchDeleteImage(any(BatchDeleteImageRequest.class)))
				.thenReturn(BatchDeleteImageResponse.builder()
						.imageIds(ImageIdentifier.builder().imageTag("it1").imageDigest("id1").build())
						.failures(Collections.emptyList())
						.build()
				);
	}

	@Test
	void deleteImage() throws Exception {
		this.stubDeletedImage();
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  ecrDeleteImage(imageIds: [[imageTag: 'it1', imageDigest: 'id1']], registryId: 'rId', repositoryName: 'rName')
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<BatchDeleteImageRequest> argumentCaptor = ArgumentCaptor.forClass(BatchDeleteImageRequest.class);
		verify(this.ecr).batchDeleteImage(argumentCaptor.capture());

		BatchDeleteImageRequest request = argumentCaptor.getValue();
		assertEquals(BatchDeleteImageRequest.builder()
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
	void returnsImageIdsAsReadableMaps() throws Exception {
		this.stubDeletedImage();
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ecrDeleteReturn");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def r = ecrDeleteImage(imageIds: [[imageTag: 'it1']], repositoryName: 'rName')
                  echo "tag=${r[0].imageTag} digest=${r[0].imageDigest}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		this.jenkinsRule.assertLogContains("tag=it1 digest=id1", run);
	}

	/**
	 * registryId is optional - it defaults to the caller's registry - so the step has to work
	 * without it.
	 */
	@Test
	void worksWithoutARegistryId() throws Exception {
		when(this.ecr.batchDeleteImage(any(BatchDeleteImageRequest.class)))
				.thenReturn(BatchDeleteImageResponse.builder().failures(Collections.emptyList()).build());
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ecrDeleteNoRegistry");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  ecrDeleteImage(imageIds: [[imageTag: 'it1']], repositoryName: 'rName')
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<BatchDeleteImageRequest> captor = ArgumentCaptor.forClass(BatchDeleteImageRequest.class);
		verify(this.ecr).batchDeleteImage(captor.capture());
		assertNull(captor.getValue().registryId());
	}

}
