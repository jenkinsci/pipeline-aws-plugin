package de.taimos.pipeline.aws.code.deploy;

import com.amazonaws.services.codedeploy.AmazonCodeDeploy;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClientBuilder;
import com.amazonaws.services.codedeploy.model.CreateDeploymentRequest;
import com.amazonaws.services.codedeploy.model.CreateDeploymentResult;
import com.amazonaws.services.codedeploy.model.FileExistsBehavior;
import com.amazonaws.services.codedeploy.model.GitHubLocation;
import com.amazonaws.services.codedeploy.model.RevisionLocation;
import com.amazonaws.services.codedeploy.model.RevisionLocationType;
import com.amazonaws.services.codedeploy.model.S3Location;
import com.amazonaws.services.codedeploy.model.GetDeploymentGroupRequest;
import com.amazonaws.services.codedeploy.model.GetDeploymentGroupResult;
import com.amazonaws.services.codedeploy.model.DeploymentGroupInfo;
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
			AmazonCodeDeploy client = AWSClientFactory.create(AmazonCodeDeployClientBuilder.standard(), this.getContext());

			listener.getLogger().format("Deploying application (%s) with group name (%s) %n", step.getApplicationName(), step.getDeploymentGroupName());

			CreateDeploymentRequest deploymentRequest = new CreateDeploymentRequest()
					.withApplicationName(step.getApplicationName())
					.withDeploymentGroupName(step.getDeploymentGroupName())
					.withDeploymentConfigName(step.getDeploymentConfigName())
					.withDescription(step.getDescription())
					.withRevision(getRevisionLocation())
					.withIgnoreApplicationStopFailures(step.getIgnoreApplicationStopFailures());

			FileExistsBehavior fileExistsBehavior = getFileExistsBehavior(step.getFileExistsBehavior());
			if (fileExistsBehavior != null) {
				deploymentRequest.withFileExistsBehavior(fileExistsBehavior);
			}

			CreateDeploymentResult deployment = client.createDeployment(deploymentRequest);

			listener.getLogger().format("DeploymentId (%s) %n", deployment.getDeploymentId());

			if (step.waitForCompletion) {
				new DeployUtils().waitDeployment(deployment.getDeploymentId(), listener, client);
			}

			listener.getLogger().println("Deployment complete");
			return null;
		}

		private FileExistsBehavior getFileExistsBehavior(String fileExistsBehavior) {
			if (StringUtils.isEmpty(fileExistsBehavior)) {
				return FileExistsBehavior.DISALLOW;
			}
			return FileExistsBehavior.fromValue(fileExistsBehavior);
		}

		private RevisionLocation getRevisionLocation() {
			if (StringUtils.isNotEmpty(step.getS3Bucket())) {
				final S3Location s3Location = new S3Location().withBucket(step.getS3Bucket())
						.withKey(step.getS3Key())
						.withBundleType(step.getS3BundleType());
				return new RevisionLocation()
						.withS3Location(s3Location)
						.withRevisionType(RevisionLocationType.S3);
			}
			final GitHubLocation gitHubLocation = new GitHubLocation().withRepository(step.getGitHubRepository())
					.withCommitId(step.getGitHubCommitId());
			return new RevisionLocation()
					.withGitHubLocation(gitHubLocation)
					.withRevisionType(RevisionLocationType.GitHub);
		}

		private static final long serialVersionUID = 1L;

	}
}
