package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateConfigurationTemplateRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateConfigurationTemplateResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.SourceConfiguration;
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

public class EBCreateConfigurationTemplateStep extends Step {
	private final String applicationName;
	private final String templateName;
	private String environmentId;
	private String solutionStackName;
	private String sourceConfigurationApplication;
	private String sourceConfigurationTemplate;
	private String description;

	@DataBoundConstructor
	public EBCreateConfigurationTemplateStep(String applicationName, String templateName) {
		this.applicationName = applicationName;
		this.templateName = templateName;
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
	public void setEnvironmentId(String environmentId) {
		this.environmentId = environmentId;
	}

	@DataBoundSetter
	public void setSolutionStackName(String solutionStackName) {
		this.solutionStackName = solutionStackName;
	}

	@DataBoundSetter
	public void setSourceConfigurationApplication(String sourceConfigurationApplication) {
		this.sourceConfigurationApplication = sourceConfigurationApplication;
	}

	@DataBoundSetter
	public void setSourceConfigurationTemplate(String sourceConfigurationTemplate) {
		this.sourceConfigurationTemplate = sourceConfigurationTemplate;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "ebCreateConfigurationTemplate";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Creates a new configuration template for an elastic beanstalk application";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		private final transient EBCreateConfigurationTemplateStep step;

		protected Execution(EBCreateConfigurationTemplateStep step, @NonNull StepContext context) {
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

			listener.getLogger().format("Creating configuration template (%s) for application (%s) %n", step.templateName, step.applicationName);

			SourceConfiguration sourceConfiguration = SourceConfiguration.builder()
					.applicationName(step.sourceConfigurationApplication)
					.templateName(step.sourceConfigurationTemplate)
					.build();

			CreateConfigurationTemplateRequest request = CreateConfigurationTemplateRequest.builder()
					.applicationName(step.applicationName)
					.templateName(step.templateName)
					.environmentId(step.environmentId)
					.description(step.description)
					.solutionStackName(step.solutionStackName)
					.sourceConfiguration(sourceConfiguration)
					.build();

			CreateConfigurationTemplateResponse result = client.createConfigurationTemplate(request);
			listener.getLogger().format(
					"Created a new configuration template (%s) for the application (%s) %n",
					result.templateName(),
					result.applicationName()
			);

			return null;
		}
	}
}
