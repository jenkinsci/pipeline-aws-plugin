package de.taimos.pipeline.aws.ecr;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.SetRepositoryPolicyRequest;
import com.amazonaws.services.ecr.model.SetRepositoryPolicyResult;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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

	public static class Execution extends SynchronousNonBlockingStepExecution<SetRepositoryPolicyResult> {

		private transient ECRSetRepositoryPolicyStep step;

		public Execution(ECRSetRepositoryPolicyStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		// https://github.com/aws/aws-sdk-java/blob/master/aws-java-sdk-ecr/src/main/java/com/amazonaws/services/ecr/model/SetRepositoryPolicyRequest.java
		// https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/ecr/model/SetRepositoryPolicyRequest.html
		@Override
		protected SetRepositoryPolicyResult run() throws Exception {
			AmazonECR ecr = AWSClientFactory.create(AmazonECRClientBuilder.standard(), this.getContext());

			SetRepositoryPolicyRequest request = new SetRepositoryPolicyRequest()
					.withRegistryId(this.step.getRegistryId())
					.withRepositoryName(this.step.getRepositoryName())
					.withPolicyText(this.step.getPolicyText());
			// https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/ecr/model/SetRepositoryPolicyResult.html
			SetRepositoryPolicyResult result = ecr.setRepositoryPolicy(request);
			return result;
		}

		private static final long serialVersionUID = 1L;

	}

}
