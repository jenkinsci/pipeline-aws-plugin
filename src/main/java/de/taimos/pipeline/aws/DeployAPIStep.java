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

package de.taimos.pipeline.aws;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import com.amazonaws.services.apigateway.AmazonApiGateway;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.apigateway.AmazonApiGatewayClient;
import com.amazonaws.services.apigateway.model.CreateDeploymentRequest;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class DeployAPIStep extends AbstractStepImpl {

	private final String stage;
	private final String api;
	private String description;
	private String[] variables;

	@DataBoundConstructor
	public DeployAPIStep(String api, String stage) {
		this.api = api;
		this.stage = stage;
	}

	public String getStage() {
		return this.stage;
	}

	public String getApi() {
		return this.api;
	}

	public String getDescription() {
		return this.description;
	}

	@DataBoundSetter
	public void setDescription(String description) {
		this.description = description;
	}

	public String[] getVariables() {
		return this.variables != null ? this.variables.clone() : null;
	}

	@DataBoundSetter
	public void setVariables(String[] variables) {
		this.variables = variables.clone();
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(Execution.class);
		}

		@Override
		public String getFunctionName() {
			return "deployAPI";
		}

		@Override
		public String getDisplayName() {
			return "Deploy the given API Gateway API";
		}
	}

	public static class Execution extends AbstractSynchronousStepExecution<Void> {

		@Inject
		private transient DeployAPIStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;

		@Override
		protected Void run() throws Exception {
			AmazonApiGateway client = AWSClientFactory.createAmazonApiGatewayClient(this.envVars);

			String stage = this.step.getStage();
			String api = this.step.getApi();

			this.listener.getLogger().format("Deploying API %s to stage %s %n", api, stage);

			CreateDeploymentRequest request = new CreateDeploymentRequest();
			request.withRestApiId(api);
			request.withStageName(stage);
			if (this.step.getDescription() != null) {
				request.withDescription(this.step.getDescription());
			}
			if (this.step.getVariables() != null && this.step.getVariables().length > 0) {
				request.withVariables(this.parseVariables(this.step.getVariables()));
			}

			client.createDeployment(request);

			this.listener.getLogger().println("Deployment complete");
			return null;
		}

		private static final long serialVersionUID = 1L;

		private Map<String, String> parseVariables(String[] variables) {
			Map<String, String> map = new HashMap<>();
			for (String var : variables) {
				int i = var.indexOf("=");
				if (i < 0) {
					throw new RuntimeException("Missing = in variable " + var);
				}
				map.put(var.substring(0, i), var.substring(i + 1));
			}
			return map;
		}
	}

}
