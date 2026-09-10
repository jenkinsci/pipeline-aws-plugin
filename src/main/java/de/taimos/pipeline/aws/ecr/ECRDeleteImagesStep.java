package de.taimos.pipeline.aws.ecr;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.BatchDeleteImageRequest;
import software.amazon.awssdk.services.ecr.model.BatchDeleteImageResponse;
import software.amazon.awssdk.services.ecr.model.ImageFailure;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

public class ECRDeleteImagesStep extends Step {

	private List<JenkinsImageIdentifier> imageIds;
	private String registryId, repositoryName;

	@DataBoundConstructor
	@SuppressWarnings("unused")
	public ECRDeleteImagesStep() {
	}

	private List<JenkinsImageIdentifier> getImageIds() {
		return imageIds;
	}

	@DataBoundSetter
	@SuppressWarnings("unused")
	public void setImageIds(List<JenkinsImageIdentifier> imageIds) {
		this.imageIds = imageIds;
	}

	private String getRegistryId() {
		return registryId;
	}

	@DataBoundSetter
	@SuppressWarnings("unused")
	public void setRegistryId(String registryId) {
		this.registryId = registryId;
	}

	private String getRepositoryName() {
		return repositoryName;
	}

	@DataBoundSetter
	@SuppressWarnings("unused")
	public void setRepositoryName(String repositoryName) {
		this.repositoryName = repositoryName;
	}

	@Override
	public StepExecution start(StepContext stepContext) throws Exception {
		return new Execution(stepContext, this);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor  {

		@Override
		public String getFunctionName() {
			return "ecrDeleteImage";
		}

		@Override
		@NonNull
		public String getDisplayName() {
			return "Delete ecr images";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<List<Map<String, String>>> {

		private transient ECRDeleteImagesStep step;

		public Execution(@NonNull StepContext context, ECRDeleteImagesStep step) {
			super(context);
			this.step = step;
		}

		@Override
		protected List<Map<String, String>> run() throws Exception {
			EcrClient ecr = AWSClientFactory.create(EcrClient.builder(), this.getContext());

			BatchDeleteImageResponse result = ecr.batchDeleteImage(BatchDeleteImageRequest.builder()
					.imageIds(this.step.getImageIds().stream()
							.map(JenkinsImageIdentifier::toImageIdentifier)
							.collect(Collectors.toList()))
					.registryId(this.step.getRegistryId())
					.repositoryName(this.step.getRepositoryName())
					.build()
			);
			if (!result.failures().isEmpty()) {
				TaskListener listener = this.getContext().get(TaskListener.class);
				listener.error("Unable to delete images:");
				for (ImageFailure failure : result.failures()) {
					listener.error("%s %s %s", failure.failureCodeAsString(), failure.failureReason(), failure.imageId());
				}
			}

			// Returned as maps rather than SDK model objects: script-security rejects field access
			// on the model in both SDKs, so the object was only ever printable. Maps make the
			// values readable from a pipeline and match what ecrListImages already returns.
			return result.imageIds().stream()
					.map(image -> {
						Map<String, String> imageId = new HashMap<>();
						imageId.put("imageTag", image.imageTag());
						imageId.put("imageDigest", image.imageDigest());
						return imageId;
					})
					.collect(Collectors.toList());
		}

		private static final long serialVersionUID = 1L;

	}

}
