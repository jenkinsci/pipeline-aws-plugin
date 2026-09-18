package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateEnvironmentRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateEnvironmentResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.UpdateEnvironmentRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.UpdateEnvironmentResponse;
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

		@NonNull
		@Override
		public String getDisplayName() {
			return "Creates a new Elastic Beanstalk environment";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		private final transient EBCreateEnvironmentStep step;

		protected Execution(EBCreateEnvironmentStep step, @NonNull StepContext context) {
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

			listener.getLogger().format("Creating environment (%s) %n", step.environmentName);

			boolean environmentExists = false;
			if (step.updateOnExisting) {
				DescribeEnvironmentsRequest describeRequest = DescribeEnvironmentsRequest.builder()
						.applicationName(step.applicationName)
						.environmentNames(Collections.singletonList(step.environmentName))
						.build();
				DescribeEnvironmentsResponse result = client.describeEnvironments(describeRequest);
				// statusAsString: v2 models the status as an enum, the comparison is against a literal
				Optional<EnvironmentDescription> environment = result.environments().stream()
						.filter(env -> !env.statusAsString().equalsIgnoreCase("Terminated"))
						.findFirst();
				environmentExists = environment.isPresent();
			}

			if (environmentExists) {
				UpdateEnvironmentRequest updateRequest = UpdateEnvironmentRequest.builder()
						.applicationName(step.applicationName)
						.environmentName(step.environmentName)
						.description(step.description)
						.templateName(step.templateName)
						.versionLabel(step.versionLabel)
						.solutionStackName(step.solutionStackName)
						.build();
				UpdateEnvironmentResponse result = client.updateEnvironment(updateRequest);

				listener.getLogger().format(
						"Updated existing environment %s (%s) with arn (%s) %n",
						result.environmentName(),
						result.environmentId(),
						result.environmentArn()
				);
				return null;
			}

			CreateEnvironmentRequest request = CreateEnvironmentRequest.builder()
					.applicationName(step.applicationName)
					.environmentName(step.environmentName)
					.description(step.description)
					.templateName(step.templateName)
					.versionLabel(step.versionLabel)
					.solutionStackName(step.solutionStackName)
					.build();

			CreateEnvironmentResponse result = client.createEnvironment(request);
			listener.getLogger().format(
					"Created environment %s (%s) with arn (%s) %n",
					result.environmentName(),
					result.environmentId(),
					result.environmentArn()
			);

			return null;
		}
	}
}
