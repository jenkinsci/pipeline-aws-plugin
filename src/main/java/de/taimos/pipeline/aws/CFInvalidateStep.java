/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
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

package de.taimos.pipeline.aws;

import java.util.Arrays;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;
import com.amazonaws.services.cloudfront.model.Paths;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFInvalidateStep extends Step {

	private final String distribution;
	private final String[] paths;

	@DataBoundConstructor
	public CFInvalidateStep(String distribution, String[] paths) {
		this.distribution = distribution;
		this.paths = paths.clone();
	}

	public String getDistribution() {
		return this.distribution;
	}

	public String[] getPaths() {
		return this.paths != null ? this.paths.clone() : null;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFInvalidateStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "cfInvalidate";
		}

		@Override
		public String getDisplayName() {
			return "Invalidate given paths in CloudFront distribution";
		}
	}

	public static class Execution extends SynchronousStepExecution<Void> {

		private final transient CFInvalidateStep step;

		public Execution(CFInvalidateStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Void run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);

			AmazonCloudFront client = AWSClientFactory.create(AmazonCloudFrontClientBuilder.standard(), this.getContext());

			String distribution = this.step.getDistribution();
			String[] paths = this.step.getPaths();

			listener.getLogger().format("Invalidating paths %s in distribution %s %n", Arrays.toString(paths), distribution);

			Paths invalidationPaths = new Paths().withItems(paths).withQuantity(paths.length);
			InvalidationBatch batch = new InvalidationBatch(invalidationPaths, Long.toString(System.currentTimeMillis()));
			client.createInvalidation(new CreateInvalidationRequest(distribution, batch));

			listener.getLogger().println("Invalidation complete");
			return null;
		}

		private static final long serialVersionUID = 1L;

	}

}
