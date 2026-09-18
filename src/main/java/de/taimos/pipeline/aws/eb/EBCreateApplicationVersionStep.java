package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationVersionResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.S3Location;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class EBCreateApplicationVersionStep extends Step {
	private final String applicationName;
	private final String versionLabel;
	private final String s3Bucket;
	private final String s3Key;
	private String description;

	@DataBoundConstructor
	public EBCreateApplicationVersionStep(String applicationName, String versionLabel, String s3Bucket, String s3Key) {
		this.applicationName = applicationName;
		this.versionLabel = versionLabel;
		this.s3Bucket = s3Bucket;
		this.s3Key = s3Key;
	}

	@Override
	public StepExecution start(StepContext stepContext) throws Exception {
		return new Execution(this, stepContext);
	}

	@DataBoundSetter
	public void setDescription(String description) {
		this.description = description;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "ebCreateApplicationVersion";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Creates a new version for an elastic beanstalk application";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		private final transient EBCreateApplicationVersionStep step;

		protected Execution(EBCreateApplicationVersionStep step, @NonNull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			ElasticBeanstalkClient client = AWSClientFactory.create(
					ElasticBeanstalkClient.builder(),
					this.getContext(),
					this.getContext().get(EnvVars.class)
			);

			listener.getLogger().format("Creating application version (%s) for application (%s) %n", step.versionLabel, step.applicationName);

			CreateApplicationVersionRequest request = CreateApplicationVersionRequest.builder()
					.applicationName(step.applicationName)
					.versionLabel(step.versionLabel)
					.sourceBundle(S3Location.builder().s3Bucket(step.s3Bucket).s3Key(step.s3Key).build())
					.description(step.description)
					.build();

			CreateApplicationVersionResponse result = client.createApplicationVersion(request);
			listener.getLogger().format(
					"Created a new version (%s) for the application (%s) with arn (%s) %n",
					step.versionLabel,
					step.applicationName,
					result.applicationVersion().applicationVersionArn()
			);

			return null;
		}
	}
}
