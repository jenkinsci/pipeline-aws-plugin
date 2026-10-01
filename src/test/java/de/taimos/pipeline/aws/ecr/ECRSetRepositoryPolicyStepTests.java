package de.taimos.pipeline.aws.ecr;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.SetRepositoryPolicyRequest;
import software.amazon.awssdk.services.ecr.model.SetRepositoryPolicyResponse;
import de.taimos.pipeline.aws.AWSClientFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.model.Run;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

@WithJenkins
class ECRSetRepositoryPolicyStepTests {

	private JenkinsRule jenkinsRule;
	private EcrClient ecr;
	private String expectedRegistryId = "my-registryId";
	private String expectedRegistryName = "my-repositoryName";
	private String expectedPolicyText = "{\"myPolicyName\": \"myPolicyValue\"}";

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

	@Test
	void getAndSetTest() {
		ECRSetRepositoryPolicyStep step = new ECRSetRepositoryPolicyStep();
		step.setRegistryId(expectedRegistryId);
		step.setRepositoryName(expectedRegistryName);
		step.setPolicyText(expectedPolicyText);
		assertEquals(expectedRegistryId, step.getRegistryId());
		assertEquals(expectedRegistryName, step.getRepositoryName());
		assertEquals(expectedPolicyText, step.getPolicyText());
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
	void ecrSetRepositoryPolicy() throws Exception {
		String expectedRegistryId = "my-registryId";
		String expectedRegistryName = "my-registryName";
		String expectedPolicyText = "{\"myPolicyName\": \"myPolicyValue\"}";
		when(this.ecr.setRepositoryPolicy(any(SetRepositoryPolicyRequest.class)))
				.thenReturn(mockSetRepositoryPolicyResult());
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def response = ecrSetRepositoryPolicy()
                  echo "registryId=${response.registryId}"
                  echo "repositoryName=${response.repositoryName}"
                  echo "policyText=${response.policyText}"
                }
                """, true)
		);
		Run run = this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		// the step returns a map, so these fields are readable from the pipeline; against the SDK
		// response object script-security rejected field access in either SDK
		this.jenkinsRule.assertLogContains("registryId=my-registryId", run);
		this.jenkinsRule.assertLogContains("repositoryName=my-repositoryName", run);
		this.jenkinsRule.assertLogContains("policyText={\"myPolicyName\": \"myPolicyValue\"}", run);

		verify(this.ecr, times(1)).setRepositoryPolicy(any(SetRepositoryPolicyRequest.class));
	}

}
