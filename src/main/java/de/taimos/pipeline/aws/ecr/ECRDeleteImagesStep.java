package de.taimos.pipeline.aws.ecr;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.BatchDeleteImageRequest;
import com.amazonaws.services.ecr.model.BatchDeleteImageResult;
import com.amazonaws.services.ecr.model.ImageFailure;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;
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

	public static class Execution extends SynchronousNonBlockingStepExecution<List<ImageIdentifier>> {

		private transient ECRDeleteImagesStep step;

		public Execution(@NonNull StepContext context, ECRDeleteImagesStep step) {
			super(context);
			this.step = step;
		}

		@Override
		protected List<ImageIdentifier> run() throws Exception {
			AmazonECR ecr = AWSClientFactory.create(AmazonECRClientBuilder.standard(), this.getContext());

			BatchDeleteImageResult result = ecr.batchDeleteImage(new BatchDeleteImageRequest()
					.withImageIds(new ArrayList<>(this.step.getImageIds()))
					.withRegistryId(this.step.getRegistryId())
					.withRepositoryName(this.step.getRepositoryName())
			);
			if (!result.getFailures().isEmpty()) {
				TaskListener listener = this.getContext().get(TaskListener.class);
				listener.error("Unable to delete images:");
				for (ImageFailure failure : result.getFailures()) {
					listener.error("%s %s %s", failure.getFailureCode(), failure.getFailureReason(), failure.getImageId());
				}
			}

			return result.getImageIds();
		}

		private static final long serialVersionUID = 1L;

	}

}
