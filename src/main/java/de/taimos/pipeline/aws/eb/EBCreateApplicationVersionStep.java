package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionResult;
import com.amazonaws.services.elasticbeanstalk.model.S3Location;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
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

		@Nonnull
		@Override
		public String getDisplayName() {
			return "Creates a new version for an elastic beanstalk application";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		private final transient EBCreateApplicationVersionStep step;

		protected Execution(EBCreateApplicationVersionStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			AWSElasticBeanstalk client = AWSElasticBeanstalkClientBuilder.defaultClient();
			listener.getLogger().format("Creating application version (%s) for application (%s) %n", step.versionLabel, step.applicationName);

			CreateApplicationVersionRequest request = new CreateApplicationVersionRequest();
			request.setApplicationName(step.applicationName);
			request.setVersionLabel(step.versionLabel);
			request.setSourceBundle(new S3Location(step.s3Bucket, step.s3Key));
			request.setDescription(step.description);

			CreateApplicationVersionResult result = client.createApplicationVersion(request);
			listener.getLogger().format(
					"Created a new version (%s) for the application (%s) with arn (%s) %n",
					step.versionLabel,
					step.applicationName,
					result.getApplicationVersion().getApplicationVersionArn()
			);

			return null;
		}
	}
}
