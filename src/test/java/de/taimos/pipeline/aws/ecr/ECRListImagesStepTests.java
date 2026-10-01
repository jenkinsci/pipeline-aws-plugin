package de.taimos.pipeline.aws.ecr;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesRequest;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;
import software.amazon.awssdk.services.ecr.paginators.ListImagesIterable;
import de.taimos.pipeline.aws.AWSClientFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;

@WithJenkins
class ECRListImagesStepTests {

	private JenkinsRule jenkinsRule;
	private EcrClient ecr;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.ecr = mock(EcrClient.class);
		AWSClientFactory.setFactoryDelegate(x -> this.ecr);
		// a real paginator over the mock, so the SDK's own paging issues the calls
		when(this.ecr.listImagesPaginator(any(ListImagesRequest.class)))
				.thenAnswer(invocation -> new ListImagesIterable(this.ecr, invocation.getArgument(0)));
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	@Test
	void listImages() throws Exception {
		when(this.ecr.listImages(any(ListImagesRequest.class)))
				.thenReturn(ListImagesResponse.builder()
						.imageIds(ImageIdentifier.builder().imageDigest("id1").imageTag("it1").build())
						.nextToken("next")
						.build())
				.thenReturn(ListImagesResponse.builder()
						.imageIds(ImageIdentifier.builder().imageDigest("id2").imageTag("it2").build())
						.build());
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def images = ecrListImages()
                  echo "imagesCount=${images.size()}"
                  echo "images=${images.toString()}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("imagesCount=2", run);
		this.jenkinsRule.assertLogContains("images=[[imageTag:it1, imageDigest:id1], [imageTag:it2, imageDigest:id2]]", run);

		verify(this.ecr, times(2)).listImages(any(ListImagesRequest.class));
	}

	private void stubSinglePage() {
		when(this.ecr.listImages(any(ListImagesRequest.class)))
				.thenReturn(ListImagesResponse.builder()
						.imageIds(ImageIdentifier.builder().imageDigest("id1").build())
						.build());
	}

	/**
	 * JenkinsListImageFilter stopped being an SDK subclass, so both the Stapler binding of
	 * filter: [tagStatus: ...] and the conversion to the v2 model are new code. A filter that is
	 * dropped or mis-bound does not throw - the step just returns every image instead of the
	 * requested subset, which matters because pipelines feed this into ecrDeleteImage.
	 */
	@Test
	void passesTheTagStatusFilterThrough() throws Exception {
		this.stubSinglePage();

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ecrListFiltered");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  ecrListImages(repositoryName: 'rName', filter: [tagStatus: 'UNTAGGED'])
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<ListImagesRequest> captor = ArgumentCaptor.forClass(ListImagesRequest.class);
		verify(this.ecr).listImages(captor.capture());
		assertEquals("UNTAGGED", captor.getValue().filter().tagStatusAsString());
	}

	/**
	 * filter is optional: omitting it must leave the request without one rather than send an empty
	 * filter object.
	 */
	@Test
	void omitsTheFilterWhenNotGiven() throws Exception {
		this.stubSinglePage();

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ecrListUnfiltered");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  ecrListImages(repositoryName: 'rName')
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<ListImagesRequest> captor = ArgumentCaptor.forClass(ListImagesRequest.class);
		verify(this.ecr).listImages(captor.capture());
		assertNull(captor.getValue().filter());
	}
}
