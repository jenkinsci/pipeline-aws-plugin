package de.taimos.pipeline.aws.ecr;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.SetRepositoryPolicyRequest;
import software.amazon.awssdk.services.ecr.model.SetRepositoryPolicyResponse;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.model.Run;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class ECRSetRepositoryPolicyStepTests {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private EcrClient ecr;
	private String expectedRegistryId = "my-registryId";
	private String expectedRegistryName = "my-repositoryName";
	private String expectedPolicyText = "{\"myPolicyName\": \"myPolicyValue\"}";

	@Before
	public void setupSdk() throws Exception {
		this.ecr = Mockito.mock(EcrClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.ecr);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
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
	public SetRepositoryPolicyResponse mockSetRepositoryPolicyResult() {
		return SetRepositoryPolicyResponse.builder()
				.registryId(expectedRegistryId)
				.repositoryName(expectedRegistryName)
				.policyText(expectedPolicyText)
				.build();
	}

	@Test
	public void ecrSetRepositoryPolicy() throws Exception {
		String expectedRegistryId = "my-registryId";
		String expectedRegistryName = "my-registryName";
		String expectedPolicyText = "{\"myPolicyName\": \"myPolicyValue\"}";
		Mockito.when(this.ecr.setRepositoryPolicy(Mockito.any(SetRepositoryPolicyRequest.class)))
				.thenReturn(mockSetRepositoryPolicyResult());
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  def response = ecrSetRepositoryPolicy()\n"
				+ "  echo \"registryId=${response.registryId}\"\n"
				+ "  echo \"repositoryName=${response.repositoryName}\"\n"
				+ "  echo \"policyText=${response.policyText}\"\n"
				+ "}\n", true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		// the step returns a map, so these fields are readable from the pipeline; against the SDK
		// response object script-security rejected field access in either SDK
		this.jenkinsRule.assertLogContains("registryId=my-registryId", run);
		this.jenkinsRule.assertLogContains("repositoryName=my-repositoryName", run);
		this.jenkinsRule.assertLogContains("policyText={\"myPolicyName\": \"myPolicyValue\"}", run);

		Mockito.verify(this.ecr, Mockito.times(1)).setRepositoryPolicy(Mockito.any(SetRepositoryPolicyRequest.class));
	}

}
