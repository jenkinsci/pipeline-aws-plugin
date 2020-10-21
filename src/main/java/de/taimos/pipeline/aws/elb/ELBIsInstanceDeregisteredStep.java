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
package de.taimos.pipeline.aws.elb;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;

import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class ELBIsInstanceDeregisteredStep extends Step {
	private String targetGroupARN;
	private String instanceID;
	private int port;

	@DataBoundConstructor
	public ELBIsInstanceDeregisteredStep(String targetGroupARN, String instanceID, int port) {
		this.targetGroupARN = targetGroupARN;
		this.instanceID = instanceID;
		this.port = port;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new ELBIsInstanceDeregisteredStep.Execution(this, context);
	}

	public String getTargetGroupARN() {
		return this.targetGroupARN;
	}

	@DataBoundSetter
	public void setTargetGroupARN(String targetGroupARN) {
		this.targetGroupARN = targetGroupARN;
	}

	public String getInstanceID() {
		return this.instanceID;
	}

	@DataBoundSetter
	public void setInstanceID(String instanceID) {
		this.instanceID = instanceID;
	}

	public int getPort() {
		return this.port;
	}

	@DataBoundSetter
	public void setPort(int port) {
		this.port = port;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {
		@Override
		public String getFunctionName() {
			return "elbIsInstanceDeregistered";
		}

		@Override
		public String getDisplayName() {
			return "Registers the specified instances from the specified load balancer.";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Boolean> {
		private final transient ELBIsInstanceDeregisteredStep step;

		public Execution(ELBIsInstanceDeregisteredStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Boolean run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			listener.getLogger().println("elbIsInstanceDeregistered instanceID: " + this.step.instanceID + " port: " + this.step.port + " from targetGroupARN: " + this.step.targetGroupARN);

			Boolean rval = true;
			AmazonElasticLoadBalancing client = AWSClientFactory.create(AmazonElasticLoadBalancingClientBuilder.standard(), this.getContext(), this.getEnvVars());
			DescribeTargetHealthRequest req = new DescribeTargetHealthRequest().withTargetGroupArn(this.step.targetGroupARN);
			DescribeTargetHealthResult res = client.describeTargetHealth(req);
			List<TargetHealthDescription> targets = res.getTargetHealthDescriptions();
			for (TargetHealthDescription target : targets) {
				if (target.getTarget().getId().equals(this.step.instanceID) ) {
					rval = false;
					break;
				}
			}
			listener.getLogger().println(res.toString());

			return rval;
		}

		public EnvVars getEnvVars() {
			try {
				return this.getContext().get(EnvVars.class);
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		private static final long serialVersionUID = 1L;
	}
}
