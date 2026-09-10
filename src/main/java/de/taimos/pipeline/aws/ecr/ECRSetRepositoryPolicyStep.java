package de.taimos.pipeline.aws.ecr;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.SetRepositoryPolicyRequest;
import software.amazon.awssdk.services.ecr.model.SetRepositoryPolicyResponse;
import de.taimos.pipeline.aws.AWSClientFactory;

import java.util.HashMap;
import java.util.Map;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class ECRSetRepositoryPolicyStep extends Step {

	private String registryId, repositoryName, policyText;

	@DataBoundConstructor
	@SuppressWarnings("unused")
	public ECRSetRepositoryPolicyStep() {
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new Execution(this, context);
	}

	public String getRegistryId() {
		return registryId;
	}

	@DataBoundSetter
	public void setRegistryId(String registryId) {
		this.registryId = registryId;
	}

	public String getPolicyText() {
		return policyText;
	}

	@DataBoundSetter
	public void setPolicyText(String policyText) {
		this.policyText = policyText;
	}

	public String getRepositoryName() {
		return repositoryName;
	}

	@DataBoundSetter
	public void setRepositoryName(String repositoryName) {
		this.repositoryName = repositoryName;
	}


	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "ecrSetRepositoryPolicy";
		}

		@Override
		@NonNull
		public String getDisplayName() {
			return "Set ECR Repository Policy";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Map<String, String>> {

		private transient ECRSetRepositoryPolicyStep step;

		public Execution(ECRSetRepositoryPolicyStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		// https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-ecr/src/main/java/com/amazonaws/services/ecr/model/SetRepositoryPolicyRequest.java
		// https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/ecr/model/SetRepositoryPolicyRequest.html
		@Override
		protected Map<String, String> run() throws Exception {
			EcrClient ecr = AWSClientFactory.create(EcrClient.builder(), this.getContext());

			SetRepositoryPolicyRequest request = SetRepositoryPolicyRequest.builder()
					.registryId(this.step.getRegistryId())
					.repositoryName(this.step.getRepositoryName())
					.policyText(this.step.getPolicyText())
					.build();
			// https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/ecr/model/SetRepositoryPolicyResult.html
			SetRepositoryPolicyResponse result = ecr.setRepositoryPolicy(request);
			// A map rather than the SDK response: script-security rejects field access on the model
			// in both SDKs, so the returned object was only ever printable.
			Map<String, String> response = new HashMap<>();
			response.put("registryId", result.registryId());
			response.put("repositoryName", result.repositoryName());
			response.put("policyText", result.policyText());
			return response;
		}

		private static final long serialVersionUID = 1L;

	}

}
