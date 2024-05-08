package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.AWSElasticBeanstalkException;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.ResourceNotFoundException;
import com.amazonaws.services.elasticbeanstalk.model.SwapEnvironmentCNAMEsRequest;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
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
			AWSElasticBeanstalk client = AWSClientFactory.create(
					AWSElasticBeanstalkClientBuilder.standard(),
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

			SwapEnvironmentCNAMEsRequest request = new SwapEnvironmentCNAMEsRequest();
			request.setSourceEnvironmentId(step.sourceEnvironmentId);
			request.setSourceEnvironmentName(step.sourceEnvironmentName);
			request.setDestinationEnvironmentId(step.destinationEnvironmentId);
			request.setDestinationEnvironmentName(step.destinationEnvironmentName);

			client.swapEnvironmentCNAMEs(request);
			listener.getLogger().format("Swapped CNAMEs for environments %s(%s) and %s(%s) %n",
					step.sourceEnvironmentName,
					step.sourceEnvironmentId,
					step.destinationEnvironmentName,
					step.destinationEnvironmentId
			);

			return null;
		}

		private void updateEnvironmentIdsFromUrl(AWSElasticBeanstalk client) {
			DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest();
			DescribeEnvironmentsResult result = client.describeEnvironments(request);

			if (result.getEnvironments().isEmpty()) {
				throw new AWSElasticBeanstalkException("No environments found. Please check the aws credentials and region");
			}

			EnvironmentDescription environment;
			if (step.sourceEnvironmentCNAME != null) {
				environment = result.getEnvironments().stream()
						.filter(env -> step.sourceEnvironmentCNAME.equalsIgnoreCase(env.getCNAME()))
						.findFirst()
						.orElseThrow(() -> new ResourceNotFoundException(
								String.format("Environment with url %s not found", step.sourceEnvironmentCNAME)));

				step.sourceEnvironmentId = environment.getEnvironmentId();
				step.sourceEnvironmentName = environment.getEnvironmentName();
			}

			if (step.destinationEnvironmentCNAME != null) {
				environment = result.getEnvironments().stream()
						.filter(env -> step.destinationEnvironmentCNAME.equalsIgnoreCase(env.getCNAME()))
						.findFirst()
						.orElseThrow(() -> new ResourceNotFoundException(
								String.format("Environment with url %s not found", step.destinationEnvironmentCNAME)));

				step.destinationEnvironmentId = environment.getEnvironmentId();
				step.destinationEnvironmentName = environment.getEnvironmentName();
			}
		}
	}
}
