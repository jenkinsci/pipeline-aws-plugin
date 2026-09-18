package de.taimos.pipeline.aws.ecr;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesRequest;
import software.amazon.awssdk.services.ecr.model.ListImagesResponse;
import software.amazon.awssdk.services.ecr.paginators.ListImagesIterable;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class ECRListImagesStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private EcrClient ecr;

	@Before
	public void setupSdk() throws Exception {
		this.ecr = Mockito.mock(EcrClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.ecr);
		// a real paginator over the mock, so the SDK's own paging issues the calls
		Mockito.when(this.ecr.listImagesPaginator(Mockito.any(ListImagesRequest.class)))
				.thenAnswer(invocation -> new ListImagesIterable(this.ecr, invocation.getArgument(0)));
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	@Test
	public void listImages() throws Exception {
		Mockito.when(this.ecr.listImages(Mockito.any(ListImagesRequest.class)))
				.thenReturn(ListImagesResponse.builder()
						.imageIds(ImageIdentifier.builder().imageDigest("id1").imageTag("it1").build())
						.nextToken("next")
						.build())
				.thenReturn(ListImagesResponse.builder()
						.imageIds(ImageIdentifier.builder().imageDigest("id2").imageTag("it2").build())
						.build());
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

		Mockito.verify(this.ecr, Mockito.times(2)).listImages(Mockito.any(ListImagesRequest.class));
	}

	private void stubSinglePage() {
		Mockito.when(this.ecr.listImages(Mockito.any(ListImagesRequest.class)))
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
	public void passesTheTagStatusFilterThrough() throws Exception {
		this.stubSinglePage();

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ecrListFiltered");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  ecrListImages(repositoryName: 'rName', filter: [tagStatus: 'UNTAGGED'])\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<ListImagesRequest> captor = ArgumentCaptor.forClass(ListImagesRequest.class);
		Mockito.verify(this.ecr).listImages(captor.capture());
		Assert.assertEquals("UNTAGGED", captor.getValue().filter().tagStatusAsString());
	}

	/**
	 * filter is optional: omitting it must leave the request without one rather than send an empty
	 * filter object.
	 */
	@Test
	public void omitsTheFilterWhenNotGiven() throws Exception {
		this.stubSinglePage();

		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "ecrListUnfiltered");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  ecrListImages(repositoryName: 'rName')\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<ListImagesRequest> captor = ArgumentCaptor.forClass(ListImagesRequest.class);
		Mockito.verify(this.ecr).listImages(captor.capture());
		Assert.assertNull(captor.getValue().filter());
	}
}
