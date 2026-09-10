package de.taimos.pipeline.aws.code.deploy;

import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.CreateDeploymentRequest;
import software.amazon.awssdk.services.codedeploy.model.CreateDeploymentResponse;
import software.amazon.awssdk.services.codedeploy.model.FileExistsBehavior;
import software.amazon.awssdk.services.codedeploy.model.GitHubLocation;
import software.amazon.awssdk.services.codedeploy.model.RevisionLocation;
import software.amazon.awssdk.services.codedeploy.model.RevisionLocationType;
import software.amazon.awssdk.services.codedeploy.model.S3Location;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentGroupRequest;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentGroupResponse;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;
import lombok.Getter;
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
			CodeDeployClient client = AWSClientFactory.create(CodeDeployClient.builder(), this.getContext());

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

			// waitForCompletion is an optional Boolean, so it is null unless the pipeline sets it;
			// dereferencing it directly threw a NullPointerException for every caller that omitted
			// it. Predates the SDK migration - the README examples all pass the flag.
			if (Boolean.TRUE.equals(step.waitForCompletion)) {
				new DeployUtils().waitDeployment(deployment.deploymentId(), listener, client);
			}

			listener.getLogger().println("Deployment complete");
			return null;
		}

		private FileExistsBehavior getFileExistsBehavior(String fileExistsBehavior) {
			// Validated before the ECS/Lambda check so a bad value is rejected the same way for
			// every compute platform. Deciding first would mean a typo hard-fails against a Server
			// deployment group and is silently dropped against an ECS one - and since
			// isEcsOrLambdaDeployment swallows every exception and returns false, a transient
			// getDeploymentGroup failure would flip which of the two a pipeline gets.
			final FileExistsBehavior behavior;
			if (fileExistsBehavior == null || fileExistsBehavior.isEmpty()) {
				behavior = FileExistsBehavior.DISALLOW;
			} else {
				behavior = FileExistsBehavior.fromValue(fileExistsBehavior);
				// v1's fromValue throws on an unrecognised value; v2 returns this sentinel, whose
				// value is null, so without this check a typo would be sent to AWS as
				// fileExistsBehavior=null and fail there with an opaque error.
				if (behavior == FileExistsBehavior.UNKNOWN_TO_SDK_VERSION) {
					throw new IllegalArgumentException("Cannot create enum from " + fileExistsBehavior + " value!");
				}
			}

			// ECS and Lambda deployments must not carry the parameter at all
			if (isEcsOrLambdaDeployment()) {
				return null;
			}
			return behavior;
		}

		private boolean isEcsOrLambdaDeployment() {
			CodeDeployClient codeDeploy = AWSClientFactory.create(CodeDeployClient.builder(), this.getContext());
			
			try {
				GetDeploymentGroupRequest request = 
					GetDeploymentGroupRequest.builder()
						.applicationName(step.getApplicationName())
						.deploymentGroupName(step.getDeploymentGroupName())
						.build();
				
				GetDeploymentGroupResponse response = 
					codeDeploy.getDeploymentGroup(request);
				
				String computePlatform = response.deploymentGroupInfo().computePlatformAsString();
				
				return "ECS".equalsIgnoreCase(computePlatform) || "Lambda".equalsIgnoreCase(computePlatform);
			} catch (Exception e) {
				// Log the exception or handle it as appropriate for your use case
				return false;
			}
		}

		private RevisionLocation getRevisionLocation() {
			if (step.getS3Bucket() != null && !step.getS3Bucket().isEmpty()) {
				final S3Location s3Location = S3Location.builder().bucket(step.getS3Bucket())
						.key(step.getS3Key())
						.bundleType(step.getS3BundleType())
						.build();
				return RevisionLocation.builder()
						.s3Location(s3Location)
						.revisionType(RevisionLocationType.S3)
						.build();
			}
			final GitHubLocation gitHubLocation = GitHubLocation.builder().repository(step.getGitHubRepository())
					.commitId(step.getGitHubCommitId())
					.build();
			return RevisionLocation.builder()
					.gitHubLocation(gitHubLocation)
					.revisionType(RevisionLocationType.GIT_HUB)
					.build();
		}

		private static final long serialVersionUID = 1L;

	}
}