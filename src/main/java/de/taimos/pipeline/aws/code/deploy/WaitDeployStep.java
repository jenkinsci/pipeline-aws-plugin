/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2017 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package de.taimos.pipeline.aws.code.deploy;

import java.io.Serial;
import java.util.Set;

import de.taimos.pipeline.aws.AWSClientFactory;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.codedeploy.AmazonCodeDeploy;
import com.amazonaws.services.codedeploy.AmazonCodeDeployClientBuilder;

import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.TaskListener;

/**
 * @author Giovanni Gargiulo
 */
public class WaitDeployStep extends Step {

	/**
	 * The DeploymentId to monitor. Example: d-3GR0HQLDN
	 */
	private final String deploymentId;

	@DataBoundConstructor
	public WaitDeployStep(String deploymentId) {
		this.deploymentId = deploymentId;
	}

	public String getDeploymentId() {
		return this.deploymentId;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new WaitDeployStep.Execution(this.deploymentId, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "awaitDeploymentCompletion";
		}

		@Override
		public String getDisplayName() {
			return "Wait for AWS CodeDeploy deployment completion";
		}

	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

		@SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "Only used when starting.")
		private final transient String deploymentId;

		public Execution(String deploymentId, StepContext context) {
			super(context);
			this.deploymentId = deploymentId;
		}

		@Override
		protected Void run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			AmazonCodeDeploy client = AWSClientFactory.create(AmazonCodeDeployClientBuilder.standard(), this.getContext());

			listener.getLogger().format("Checking Deployment(%s) status", this.deploymentId);

			return new DeployUtils().waitDeployment(this.deploymentId, listener, client);
		}

		@Serial
		private static final long serialVersionUID = 1L;

	}

}
