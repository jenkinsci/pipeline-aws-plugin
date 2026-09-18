package de.taimos.pipeline.aws.ecr;

import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ListImagesRequest;
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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
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
		@NonNull
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
			EcrClient ecr = AWSClientFactory.create(EcrClient.builder(), this.getContext());

			ListImagesRequest.Builder request = ListImagesRequest.builder()
					.registryId(this.step.getRegistryId())
					.repositoryName(this.step.getRepositoryName());
			if (this.step.getFilter() != null) {
				request.filter(this.step.getFilter().toListImagesFilter());
			}
			List<ImageIdentifier> images = ecr.listImagesPaginator(request.build())
					.stream()
					.flatMap(page -> page.imageIds().stream())
					.collect(Collectors.toList());
			return images.stream().map(image -> new HashMap<String, String>() {
				{
					put("imageTag", image.imageTag());
					put("imageDigest", image.imageDigest());
				}
			}).collect(Collectors.toList());
		}

		private static final long serialVersionUID = 1L;

	}

}
