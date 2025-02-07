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

import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.AuthorizationData;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;

public class ECRLoginStep extends Step {

	private Boolean email = false;
	private List<String> registryIds;

	@DataBoundConstructor
	public ECRLoginStep() {
		//
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new ECRLoginStep.Execution(this, context);
	}

	@DataBoundSetter
	public void setEmail(Boolean email) {
		this.email = email;
	}

	public Boolean getEmail() {
		return this.email;
	}

	@DataBoundSetter
	public void setRegistryIds(List<String> registryIds) {
		this.registryIds = registryIds;
	}

	public List<String> getRegistryIds() {
		return this.registryIds;
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "ecrLogin";
		}

		@Override
		public String getDisplayName() {
			return "Create and return the ECR login string";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<String> {

		private final transient ECRLoginStep step;

		public Execution(ECRLoginStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected String run() throws Exception {
			AmazonECR ecr = AWSClientFactory.create(AmazonECRClientBuilder.standard(), this.getContext());

			GetAuthorizationTokenRequest request = new GetAuthorizationTokenRequest();
			List<String> registryIds;
			if((registryIds = this.step.getRegistryIds()) != null) {
				request.setRegistryIds(registryIds);
			}
			GetAuthorizationTokenResult token = ecr.getAuthorizationToken(request);

			if (token.getAuthorizationData().size() != 1) {
				throw new RuntimeException("Did not get authorizationData from AWS");
			}

			AuthorizationData authorizationData = token.getAuthorizationData().get(0);
			byte[] bytes = org.apache.commons.codec.binary.Base64.decodeBase64(authorizationData.getAuthorizationToken());
			String data = new String(bytes, StandardCharsets.UTF_8);
			String[] parts = data.split(":");
			if (parts.length != 2) {
				throw new RuntimeException("Got invalid authorizationData from AWS");
			}

			String emailString = this.step.getEmail() ? "-e none" : "";
			return String.format("docker login -u %s -p %s %s %s", parts[0], parts[1], emailString, authorizationData.getProxyEndpoint());
		}

		@Serial
		private static final long serialVersionUID = 1L;

	}

}
