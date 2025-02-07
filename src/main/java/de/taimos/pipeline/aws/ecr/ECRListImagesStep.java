package de.taimos.pipeline.aws.ecr;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import jakarta.annotation.Nonnull;

import java.io.Serial;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ECRListImagesStep extends Step {

	private String registryId, repositoryName;
	private JenkinsListImageFilter filter;

	@DataBoundConstructor
	@SuppressWarnings("unused")
	public ECRListImagesStep() {
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new Execution(this, context);
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

	private JenkinsListImageFilter getFilter() {
		return filter;
	}

	@DataBoundSetter
	@SuppressWarnings("unused")
	public void setFilter(JenkinsListImageFilter filter) {
		this.filter = filter;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "ecrListImages";
		}

		@Override
		@Nonnull
		public String getDisplayName() {
			return "List ECR Images";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<List<Map<String, String>>> {

		private transient ECRListImagesStep step;

		public Execution(ECRListImagesStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected List<Map<String, String>> run() throws Exception {
			AmazonECR ecr = AWSClientFactory.create(AmazonECRClientBuilder.standard(), this.getContext());

			ListImagesRequest request = new ListImagesRequest()
					.withRegistryId(this.step.getRegistryId())
					.withRepositoryName(this.step.getRepositoryName())
					.withFilter(this.step.getFilter());
			List<ImageIdentifier> images = new LinkedList<>();
			ListImagesResult result;
			do {
				result = ecr.listImages(request);
				images.addAll(result.getImageIds());
				request.setNextToken(result.getNextToken());
			} while (result.getNextToken() != null);
			return images.stream().map(image -> new HashMap<String, String>() {
				{
					put("imageTag", image.getImageTag());
					put("imageDigest", image.getImageDigest());
				}
			}).collect(Collectors.toList());
		}

		@Serial
		private static final long serialVersionUID = 1L;

	}

}
