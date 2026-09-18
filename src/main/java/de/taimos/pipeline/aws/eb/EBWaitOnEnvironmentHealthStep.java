package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.ElasticBeanstalkException;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Set;

public class EBWaitOnEnvironmentHealthStep extends Step {
	private final String applicationName;
	private final String environmentName;
	private String health = "Green";
	private int stabilityThreshold = 60;

	@DataBoundConstructor
	public EBWaitOnEnvironmentHealthStep(String applicationName, String environmentName) {
		this.applicationName = applicationName;
		this.environmentName = environmentName;
	}

	@DataBoundSetter
	public void setHealth(String health) {
		this.health = health;
	}

	@DataBoundSetter
	public void setStabilityThreshold(int stabilityThreshold) {
		this.stabilityThreshold = stabilityThreshold;
	}

	@Override
	public StepExecution start(StepContext stepContext) throws Exception {
		return new Execution(this, stepContext);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "ebWaitOnEnvironmentHealth";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Waits until the specified environment application becomes available";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		private final transient EBWaitOnEnvironmentHealthStep step;

		protected Execution(EBWaitOnEnvironmentHealthStep step, @NonNull StepContext context) {
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

			listener.getLogger().format("Waiting on environment %s health... %n", step.environmentName);

			DescribeEnvironmentsRequest request = DescribeEnvironmentsRequest.builder()
					.applicationName(step.applicationName)
					.environmentNames(Collections.singletonList(step.environmentName))
					.build();
			long startTime = System.currentTimeMillis();
			while (true) {
				DescribeEnvironmentsResponse result = client.describeEnvironments(request);

				if (result.environments().isEmpty()) {
					throw ElasticBeanstalkException.builder().message("Environment not found").build();
				}

				EnvironmentDescription environment = result.environments().get(0);
				listener.getLogger().format(
						"Environment Health: %s (%s) %n",
						environment.healthAsString(),
						environment.healthStatusAsString()
				);

				if (environment.healthAsString().equalsIgnoreCase(step.health)) {
					long stableFor = System.currentTimeMillis() - startTime;
					if(stableFor > step.stabilityThreshold * 1000) {
						return null;
					}
				} else {
					startTime = System.currentTimeMillis();
				}

				Thread.sleep(10_000);
			}
		}
	}
}
