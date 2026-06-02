package de.taimos.pipeline.aws.code.deploy;

import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.ComputePlatform;
import software.amazon.awssdk.services.codedeploy.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.codedeploy.model.CreateDeploymentResponse;
import software.amazon.awssdk.services.codedeploy.model.FileExistsBehavior;
import software.amazon.awssdk.services.codedeploy.model.GitHubLocation;
import software.amazon.awssdk.services.codedeploy.model.RevisionLocation;
import software.amazon.awssdk.services.codedeploy.model.RevisionLocationType;
import software.amazon.awssdk.services.codedeploy.model.S3Location;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentGroupRequest;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentGroupResponse;
import software.amazon.awssdk.services.codedeploy.model.DeploymentGroupInfo;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Set;


/**
 * @author Mykhaylo Gnylorybov
 */
@Getter
public class CreateDeployStep extends Step {

	private String s3Bucket;
	private String s3Key;
	private String s3BundleType;

	private String gitHubRepository;
	private String gitHubCommitId;

	private String applicationName;
	private String deploymentGroupName;
	private String deploymentConfigName;
	private String description;

	private Boolean ignoreApplicationStopFailures;
	private String fileExistsBehavior;

	private Boolean waitForCompletion;

	@DataBoundConstructor
	public CreateDeployStep() {

	}

	@Override
	public StepExecution start(StepContext stepContext) throws Exception {
		return new CreateDeployStep.Execution(this, stepContext);
	}

	@DataBoundSetter
	public void setS3Bucket(String s3Bucket) {
		this.s3Bucket = s3Bucket;
	}

	@DataBoundSetter
	public void setS3Key(String s3Key) {
		this.s3Key = s3Key;
	}

	@DataBoundSetter
	public void setS3BundleType(String s3BundleType) {
		this.s3BundleType = s3BundleType;
	}

	@DataBoundSetter
	public void setGitHubRepository(String gitHubRepository) {
		this.gitHubRepository = gitHubRepository;
	}

	@DataBoundSetter
	public void setGitHubCommitId(String gitHubCommitId) {
		this.gitHubCommitId = gitHubCommitId;
	}

	@DataBoundSetter
	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	@DataBoundSetter
	public void setDeploymentGroupName(String deploymentGroupName) {
		this.deploymentGroupName = deploymentGroupName;
	}

	@DataBoundSetter
	public void setDeploymentConfigName(String deploymentConfigName) {
		this.deploymentConfigName = deploymentConfigName;
	}

	@DataBoundSetter
	public void setDescription(String description) {
		this.description = description;
	}

	@DataBoundSetter
	public void setWaitForCompletion(Boolean waitForCompletion) {
		this.waitForCompletion = waitForCompletion;
	}

	@DataBoundSetter
	public void setIgnoreApplicationStopFailures(Boolean ignoreApplicationStopFailures) {
		this.ignoreApplicationStopFailures = ignoreApplicationStopFailures;
	}

	@DataBoundSetter
	public void setFileExistsBehavior(String fileExistsBehavior) {
		this.fileExistsBehavior = fileExistsBehavior;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "createDeployment";
		}

		@Override
		public String getDisplayName() {
			return "Deploys an application revision through the specified deployment group (AWS CodeDeploy).";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

		private final transient CreateDeployStep step;

		public Execution(CreateDeployStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			CodeDeployClient client = AWSClientFactory.create(CodeDeployClient.builder(), this.getContext()).build();

			listener.getLogger().format("Deploying application (%s) with group name (%s) %n", step.getApplicationName(), step.getDeploymentGroupName());

			CreateDeploymentRequest.Builder deploymentRequest = CreateDeploymentRequest.builder()
					.applicationName(step.getApplicationName())
					.deploymentGroupName(step.getDeploymentGroupName())
					.deploymentConfigName(step.getDeploymentConfigName())
					.description(step.getDescription())
					.revision(getRevisionLocation())
					.ignoreApplicationStopFailures(step.getIgnoreApplicationStopFailures());

			FileExistsBehavior fileExistsBehavior = getFileExistsBehavior(step.getFileExistsBehavior());
			if (fileExistsBehavior != null) {
				deploymentRequest.fileExistsBehavior(fileExistsBehavior);
			}

			CreateDeploymentResponse deployment = client.createDeployment(deploymentRequest.build());

			listener.getLogger().format("DeploymentId (%s) %n", deployment.deploymentId());

			if (step.waitForCompletion) {
				new DeployUtils().waitDeployment(deployment.deploymentId(), listener, client);
			}

			listener.getLogger().println("Deployment complete");
			return null;
		}

		private FileExistsBehavior getFileExistsBehavior(String fileExistsBehavior) {
			if (isEcsOrLambdaDeployment()) {
				return null;
			}
			if (StringUtils.isEmpty(fileExistsBehavior)) {
				return FileExistsBehavior.DISALLOW;
			}
			return FileExistsBehavior.fromValue(fileExistsBehavior);
		}

		private boolean isEcsOrLambdaDeployment() {
			CodeDeployClient codeDeploy = AWSClientFactory.create(CodeDeployClient.builder(), this.getContext()).build();
			
			try {
				GetDeploymentGroupRequest request = 
					GetDeploymentGroupRequest.builder()
						.applicationName(step.getApplicationName())
						.deploymentGroupName(step.getDeploymentGroupName()).build();
				
				GetDeploymentGroupResponse response =
					codeDeploy.getDeploymentGroup(request);
				
				ComputePlatform computePlatform = response.deploymentGroupInfo().computePlatform();
				
				return computePlatform == ComputePlatform.ECS || computePlatform == ComputePlatform.LAMBDA;
			} catch (Exception e) {
				// Log the exception or handle it as appropriate for your use case
				return false;
			}
		}

		private RevisionLocation getRevisionLocation() {
			if (StringUtils.isNotEmpty(step.getS3Bucket())) {
				final S3Location s3Location = S3Location.builder().bucket(step.getS3Bucket())
						.key(step.getS3Key())
						.bundleType(step.getS3BundleType()).build();
				return RevisionLocation.builder()
						.s3Location(s3Location)
						.revisionType(RevisionLocationType.S3).build();
			}
			final GitHubLocation gitHubLocation = GitHubLocation.builder().repository(step.getGitHubRepository())
					.commitId(step.getGitHubCommitId()).build();
			return RevisionLocation.builder()
					.gitHubLocation(gitHubLocation)
					.revisionType(RevisionLocationType.GIT_HUB).build();
		}

		private static final long serialVersionUID = 1L;

	}
}