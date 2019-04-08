package de.taimos.pipeline.aws.ecr;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.BatchDeleteImageRequest;
import com.amazonaws.services.ecr.model.BatchDeleteImageResult;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesResult;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
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

import java.util.Collections;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
		value = AWSClientFactory.class,
		fullyQualifiedNames = "de.taimos.pipeline.aws.cloudformation.*"
)
@PowerMockIgnore("javax.crypto.*")
public class ECRDeleteImagesStepTests {

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
