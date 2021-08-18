package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentRequest;
import com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentResult;
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

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class EBCreateEnvironmentStep extends Step {
	private final String applicationName;
	private final String environmentName;
	private String description;
	private String templateName;
	private String solutionStackName;
	private String versionLabel;
	private boolean updateOnExisting = true;

	@DataBoundConstructor
	public EBCreateEnvironmentStep(String applicationName, String environmentName) {
		this.applicationName = applicationName;
		this.environmentName = environmentName;
	}

	@Override
	public StepExecution start(StepContext stepContext) throws Exception {
		return new Execution(this, stepContext);
	}

	@DataBoundSetter
	public void setDescription(String description) {
		this.description = description;
	}

	@DataBoundSetter
	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	@DataBoundSetter
	public void setSolutionStackName(String solutionStackName) {
		this.solutionStackName = solutionStackName;
	}

	@DataBoundSetter
	public void setVersionLabel(String versionLabel) {
		this.versionLabel = versionLabel;
	}

	@DataBoundSetter
	public void setUpdateOnExisting(boolean updateOnExisting) {
		this.updateOnExisting = updateOnExisting;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "ebCreateEnvironment";
		}

		@Nonnull
		@Override
		public String getDisplayName() {
			return "Creates a new Elastic Beanstalk environment";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		private final transient EBCreateEnvironmentStep step;

		protected Execution(EBCreateEnvironmentStep step, @Nonnull StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			AWSElasticBeanstalk client = AWSClientFactory.create(
					AWSElasticBeanstalkClientBuilder.standard(),
					this.getContext(),
					this.getContext().get(EnvVars.class)
			);

			listener.getLogger().format("Creating environment (%s) %n", step.environmentName);

			boolean environmentExists = false;
			if (step.updateOnExisting) {
				DescribeEnvironmentsRequest describeRequest = new DescribeEnvironmentsRequest();
				describeRequest.setApplicationName(step.applicationName);
				describeRequest.setEnvironmentNames(Collections.singletonList(step.environmentName));
				DescribeEnvironmentsResult result = client.describeEnvironments(describeRequest);
				Optional<EnvironmentDescription> environment = result.getEnvironments().stream()
						.filter(env -> !env.getStatus().equalsIgnoreCase("Terminated"))
						.findFirst();
				environmentExists = environment.isPresent();
			}

			if (environmentExists) {
				UpdateEnvironmentRequest updateRequest = new UpdateEnvironmentRequest();
				updateRequest.setApplicationName(step.applicationName);
				updateRequest.setEnvironmentName(step.environmentName);
				updateRequest.setDescription(step.description);
				updateRequest.setTemplateName(step.templateName);
				updateRequest.setVersionLabel(step.versionLabel);
				updateRequest.setSolutionStackName(step.solutionStackName);
				UpdateEnvironmentResult result = client.updateEnvironment(updateRequest);

				listener.getLogger().format(
						"Updated existing environment %s (%s) with arn (%s) %n",
						result.getEnvironmentName(),
						result.getEnvironmentId(),
						result.getEnvironmentArn()
				);
				return null;
			}

			CreateEnvironmentRequest request = new CreateEnvironmentRequest();
			request.setApplicationName(step.applicationName);
			request.setEnvironmentName(step.environmentName);
			request.setDescription(step.description);
			request.setTemplateName(step.templateName);
			request.setVersionLabel(step.versionLabel);
			request.setSolutionStackName(step.solutionStackName);

			CreateEnvironmentResult result = client.createEnvironment(request);
			listener.getLogger().format(
					"Created environment %s (%s) with arn (%s) %n",
					result.getEnvironmentName(),
					result.getEnvironmentId(),
					result.getEnvironmentArn()
			);

			return null;
		}
	}
}
