package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.ElasticBeanstalkException;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.ResourceNotFoundException;
import software.amazon.awssdk.services.elasticbeanstalk.model.SwapEnvironmentCnamEsRequest;
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
import java.util.Set;

public class EBSwapEnvironmentCNAMEsStep extends Step {
	private String sourceEnvironmentId;
	private String sourceEnvironmentName;
	private String sourceEnvironmentCNAME;
	private String destinationEnvironmentId;
	private String destinationEnvironmentName;
	private String destinationEnvironmentCNAME;

	@DataBoundConstructor
	public EBSwapEnvironmentCNAMEsStep() {
	}

	@Override
	public StepExecution start(StepContext stepContext) throws Exception {
		return new Execution(this, stepContext);
	}

	@DataBoundSetter
	public void setSourceEnvironmentId(String sourceEnvironmentId) {
		this.sourceEnvironmentId = sourceEnvironmentId;
	}

	@DataBoundSetter
	public void setSourceEnvironmentName(String sourceEnvironmentName) {
		this.sourceEnvironmentName = sourceEnvironmentName;
	}

	@DataBoundSetter
	public void setDestinationEnvironmentId(String destinationEnvironmentId) {
		this.destinationEnvironmentId = destinationEnvironmentId;
	}

	@DataBoundSetter
	public void setDestinationEnvironmentName(String destinationEnvironmentName) {
		this.destinationEnvironmentName = destinationEnvironmentName;
	}

	@DataBoundSetter
	public void setDestinationEnvironmentCNAME(String destinationEnvironmentCNAME) {
		this.destinationEnvironmentCNAME = destinationEnvironmentCNAME;
	}

	@DataBoundSetter
	public void setSourceEnvironmentCNAME(String sourceEnvironmentCNAME) {
		this.sourceEnvironmentCNAME = sourceEnvironmentCNAME;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "ebSwapEnvironmentCNAMEs";
		}

		@NonNull
		@Override
		public String getDisplayName() {
			return "Swaps the CNAMEs of two elastic beanstalk environments.";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		private final transient EBSwapEnvironmentCNAMEsStep step;

		protected Execution(EBSwapEnvironmentCNAMEsStep step, @NonNull StepContext context) {
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

			if (step.sourceEnvironmentCNAME != null || step.destinationEnvironmentCNAME != null) {
				listener.getLogger().format("Looking up identifiers based on CNAMEs provided %n");
				updateEnvironmentIdsFromUrl(client);
			}

			listener.getLogger().format("Swapping CNAMEs for environments %s(%s) and %s(%s) %n",
					step.sourceEnvironmentName,
					step.sourceEnvironmentId,
					step.destinationEnvironmentName,
					step.destinationEnvironmentId
			);

			SwapEnvironmentCnamEsRequest request = SwapEnvironmentCnamEsRequest.builder()
					.sourceEnvironmentId(step.sourceEnvironmentId)
					.sourceEnvironmentName(step.sourceEnvironmentName)
					.destinationEnvironmentId(step.destinationEnvironmentId)
					.destinationEnvironmentName(step.destinationEnvironmentName)
					.build();

			client.swapEnvironmentCNAMEs(request);
			listener.getLogger().format("Swapped CNAMEs for environments %s(%s) and %s(%s) %n",
					step.sourceEnvironmentName,
					step.sourceEnvironmentId,
					step.destinationEnvironmentName,
					step.destinationEnvironmentId
			);

			return null;
		}

		private void updateEnvironmentIdsFromUrl(ElasticBeanstalkClient client) {
			DescribeEnvironmentsRequest request = DescribeEnvironmentsRequest.builder().build();
			DescribeEnvironmentsResponse result = client.describeEnvironments(request);

			if (result.environments().isEmpty()) {
				throw ElasticBeanstalkException.builder().message("No environments found. Please check the aws credentials and region").build();
			}

			EnvironmentDescription environment;
			if (step.sourceEnvironmentCNAME != null) {
				environment = result.environments().stream()
						.filter(env -> step.sourceEnvironmentCNAME.equalsIgnoreCase(env.cname()))
						.findFirst()
						.orElseThrow(() -> ResourceNotFoundException.builder()
								.message(String.format("Environment with url %s not found", step.sourceEnvironmentCNAME))
								.build());

				step.sourceEnvironmentId = environment.environmentId();
				step.sourceEnvironmentName = environment.environmentName();
			}

			if (step.destinationEnvironmentCNAME != null) {
				environment = result.environments().stream()
						.filter(env -> step.destinationEnvironmentCNAME.equalsIgnoreCase(env.cname()))
						.findFirst()
						.orElseThrow(() -> ResourceNotFoundException.builder()
								.message(String.format("Environment with url %s not found", step.destinationEnvironmentCNAME))
								.build());

				step.destinationEnvironmentId = environment.environmentId();
				step.destinationEnvironmentName = environment.environmentName();
			}
		}
	}
}
