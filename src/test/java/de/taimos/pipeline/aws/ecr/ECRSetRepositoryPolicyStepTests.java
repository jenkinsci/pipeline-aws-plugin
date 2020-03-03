package de.taimos.pipeline.aws.ecr;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.SetRepositoryPolicyResult;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
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
		fullyQualifiedNames = "de.taimos.pipeline.aws.ecr.*"
)
@PowerMockIgnore("javax.crypto.*")
public class ECRSetRepositoryPolicyStepTests {

	@Rule
	private JenkinsRule jenkinsRule = new JenkinsRule();
	private AmazonECR ecr;
	private String expectedRegistryId = "my-registryId";
	private String expectedRegistryName = "my-repositoryName";
	private String expectedPolicyText = "{\"myPolicyName\": \"myPolicyValue\"}";

	@Before
	public void setupSdk() throws Exception {
		this.ecr = Mockito.mock(AmazonECR.class);
		PowerMockito.mockStatic(AWSClientFactory.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(StepContext.class)))
				.thenReturn(ecr);
	}

	@Test
	public void getAndSetTest() throws Exception {
		ECRSetRepositoryPolicyStep step = new ECRSetRepositoryPolicyStep();
		step.setRegistryId(expectedRegistryId);
		step.setRepositoryName(expectedRegistryName);
		step.setPolicyText(expectedPolicyText);
		Assert.assertEquals(expectedRegistryId, step.getRegistryId());
		Assert.assertEquals(expectedRegistryName, step.getRepositoryName());
		Assert.assertEquals(expectedPolicyText, step.getPolicyText());
	}

	@Whitelisted
	public SetRepositoryPolicyResult mockSetRepositoryPolicyResult(){
		return new SetRepositoryPolicyResult()
				.withRegistryId(expectedRegistryId)
				.withRepositoryName(expectedRegistryName)
				.withPolicyText(expectedPolicyText);
	}

	@Test
	public void ecrSetRepositoryPolicy() throws Exception {
		String expectedRegistryId = "my-registryId";
		String expectedRegistryName = "my-registryName";
		String expectedPolicyText = "{\"myPolicyName\": \"myPolicyValue\"}";
		Mockito.when(ecr.setRepositoryPolicy(Mockito.any())).thenReturn(mockSetRepositoryPolicyResult());
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def response = ecrSetRepositoryPolicy()\n"
				+ "  echo \"${response.toString()}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		this.jenkinsRule.assertLogContains("RegistryId: my-registryId,", run);
		this.jenkinsRule.assertLogContains("RepositoryName: my-repositoryName,", run);

		Mockito.verify(this.ecr, Mockito.times(1)).setRepositoryPolicy(Mockito.any());
	}

}
