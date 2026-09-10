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

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Export;
import software.amazon.awssdk.services.cloudformation.model.ListExportsRequest;
import software.amazon.awssdk.services.cloudformation.model.ListExportsResponse;

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
			CloudFormationClient client = AWSClientFactory.create(CloudFormationClient.builder(), Execution.this.getContext());
			return Execution.this.getExports(client);
		}

		/**
		 * v1 walked nextToken by hand and recursed; the paginator issues the same sequence.
		 */
		private Map<String, String> getExports(CloudFormationClient client) {
			Map<String, String> map = new HashMap<>();
			for (ListExportsResponse page : client.listExportsPaginator(ListExportsRequest.builder().build())) {
				for (Export export : page.exports()) {
					map.put(export.name(), export.value());
				}
			}
			return map;
		}

		private static final long serialVersionUID = 1L;

	}

}
