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
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.CreateSAMLProviderRequest;
import com.amazonaws.services.identitymanagement.model.CreateSAMLProviderResult;
import com.amazonaws.services.identitymanagement.model.ListSAMLProvidersResult;
import com.amazonaws.services.identitymanagement.model.SAMLProviderListEntry;
import com.amazonaws.services.identitymanagement.model.UpdateSAMLProviderRequest;
import com.amazonaws.services.identitymanagement.model.UpdateSAMLProviderResult;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class UpdateIdP extends Step {

	private final String name;
	private final String metadata;

	@DataBoundConstructor
	public UpdateIdP(String name, String metadata) {
		this.name = name;
		this.metadata = metadata;
	}

	public String getName() {
		return this.name;
	}

	public String getMetadata() {
		return this.metadata;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new UpdateIdP.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
		}

		@Override
		public String getFunctionName() {
			return "updateIdP";
		}

		@Override
		public String getDisplayName() {
			return "Update thirdparty Identity Provider";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<String> {

		private final transient UpdateIdP step;

		public Execution(UpdateIdP step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected String run() throws Exception {
			final String name = this.step.getName();
			final String metadata = this.step.getMetadata();

			Preconditions.checkArgument(name != null && !name.isEmpty(), "name must not be null or empty");
			Preconditions.checkArgument(metadata != null && !metadata.isEmpty(), "metadata must not be null or empty");

			TaskListener listener = Execution.this.getContext().get(TaskListener.class);
			AmazonIdentityManagement iamClient = AWSClientFactory.create(AmazonIdentityManagementClientBuilder.standard(), Execution.this.getContext());

			listener.getLogger().format("Checking for identity provider %s %n", name);
			ListSAMLProvidersResult listResult = iamClient.listSAMLProviders();

			String providerARN = null;
			for (SAMLProviderListEntry entry : listResult.getSAMLProviderList()) {
				String entryArn = entry.getArn();
				String entryName = entryArn.substring(entryArn.lastIndexOf('/') + 1);
				if (entryName.equals(name)) {
					providerARN = entryArn;
					break;
				}
			}

			String metadataDocument = Execution.this.getContext().get(FilePath.class).child(metadata).readToString();

			if (providerARN != null) {
				// Update IdP
				UpdateSAMLProviderRequest request = new UpdateSAMLProviderRequest();
				request.withSAMLProviderArn(providerARN);
				request.withSAMLMetadataDocument(metadataDocument);
				UpdateSAMLProviderResult result = iamClient.updateSAMLProvider(request);
				listener.getLogger().format("Updated identity provider %s %n", result.getSAMLProviderArn());
			} else {
				// Create IdP
				CreateSAMLProviderRequest request = new CreateSAMLProviderRequest();
				request.withName(name);
				request.withSAMLMetadataDocument(metadataDocument);
				CreateSAMLProviderResult result = iamClient.createSAMLProvider(request);
				providerARN = result.getSAMLProviderArn();
				listener.getLogger().format("Created identity provider %s %n", providerARN);
			}
			return providerARN;
		}

		@Serial
		private static final long serialVersionUID = 1L;

	}

}
