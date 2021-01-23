package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.SwapEnvironmentCNAMEsRequest;
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

import javax.annotation.Nonnull;
import java.util.Set;

public class EBSwapEnvironmentCNAMEsStep extends Step {
	private String sourceEnvironmentId;
	private String sourceEnvironmentName;
	private String destinationEnvironmentId;
	private String destinationEnvironmentName;

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

		@Nonnull
		@Override
		public String getDisplayName() {
			return "Swaps the CNAMEs of two elastic beanstalk environments.";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
		private static final long serialVersionUID = 1L;
		private final transient EBSwapEnvironmentCNAMEsStep step;

		protected Execution(EBSwapEnvironmentCNAMEsStep step, @Nonnull StepContext context) {
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
	}
}
