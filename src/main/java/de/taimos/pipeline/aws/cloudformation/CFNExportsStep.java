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

package de.taimos.pipeline.aws.cloudformation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.Export;
import com.amazonaws.services.cloudformation.model.ListExportsRequest;
import com.amazonaws.services.cloudformation.model.ListExportsResult;

import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFNExportsStep extends Step {

	@DataBoundConstructor
	public CFNExportsStep() {
		//
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new CFNExportsStep.Execution(context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "cfnExports";
		}

		@Override
		public String getDisplayName() {
			return "Describe CloudFormation global exports";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Map<String, String>> {

		private transient CFNExportsStep step;

		public Execution(StepContext context) {
			super(context);
		}

		@Override
		protected Map<String, String> run() throws Exception {
			this.getContext().get(TaskListener.class).getLogger().format("Getting global exports of CloudFormation %n");
			AmazonCloudFormation client = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), Execution.this.getContext());
			return Execution.this.getExports(client, null);
		}

		private Map<String, String> getExports(AmazonCloudFormation client, String nextToken) {
			ListExportsResult exports = client.listExports(new ListExportsRequest().withNextToken(nextToken));

			Map<String, String> map = new HashMap<>();
			for (Export export : exports.getExports()) {
				map.put(export.getName(), export.getValue());
			}
			if (exports.getNextToken() != null) {
				map.putAll(this.getExports(client, exports.getNextToken()));
			}
			return map;
		}

		private static final long serialVersionUID = 1L;

	}

}
