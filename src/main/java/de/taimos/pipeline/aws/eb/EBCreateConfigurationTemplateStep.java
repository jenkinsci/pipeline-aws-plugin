package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.CreateConfigurationTemplateRequest;
import com.amazonaws.services.elasticbeanstalk.model.CreateConfigurationTemplateResult;
import com.amazonaws.services.elasticbeanstalk.model.SourceConfiguration;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
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
			AWSElasticBeanstalk client = AWSClientFactory.create(
					AWSElasticBeanstalkClientBuilder.standard(),
					this.getContext(),
					this.getContext().get(EnvVars.class)
			);

			listener.getLogger().format("Creating configuration template (%s) for application (%s) %n", step.templateName, step.applicationName);

			CreateConfigurationTemplateRequest request = new CreateConfigurationTemplateRequest();
			request.setApplicationName(step.applicationName);
			request.setTemplateName(step.templateName);
			request.setEnvironmentId(step.environmentId);
			request.setDescription(step.description);
			request.setSolutionStackName(step.solutionStackName);

			SourceConfiguration sourceConfiguration = new SourceConfiguration();
			sourceConfiguration.setApplicationName(step.sourceConfigurationApplication);
			sourceConfiguration.setTemplateName(step.sourceConfigurationTemplate);
			request.setSourceConfiguration(sourceConfiguration);

			CreateConfigurationTemplateResult result = client.createConfigurationTemplate(request);
			listener.getLogger().format(
					"Created a new configuration template (%s) for the application (%s) %n",
					result.getTemplateName(),
					result.getTemplateName()
			);

			return null;
		}
	}
}
