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

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.CreateSAMLProviderRequest;
import com.amazonaws.services.identitymanagement.model.CreateSAMLProviderResult;
import com.amazonaws.services.identitymanagement.model.ListSAMLProvidersResult;
import com.amazonaws.services.identitymanagement.model.SAMLProviderListEntry;
import com.amazonaws.services.identitymanagement.model.UpdateSAMLProviderRequest;
import com.amazonaws.services.identitymanagement.model.UpdateSAMLProviderResult;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class UpdateIdP extends AbstractStepImpl {
	
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
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
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
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient UpdateIdP step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String name = this.step.getName();
			final String metadata = this.step.getMetadata();
			
			new Thread("updateIDP") {
				@Override
				public void run() {
					try {
						AmazonIdentityManagementClient iamClient = AWSClientFactory.create(AmazonIdentityManagementClient.class, Execution.this.envVars);
						
						Execution.this.listener.getLogger().format("Checking for identity provider %s %n", name);
						ListSAMLProvidersResult listResult = iamClient.listSAMLProviders();
						
						String providerARN = null;
						for (SAMLProviderListEntry entry : listResult.getSAMLProviderList()) {
							String entryArn = entry.getArn();
							String entryName = entryArn.substring(entryArn.lastIndexOf("/") + 1);
							if (entryName.equals(name)) {
								providerARN = entryArn;
								break;
							}
						}
						
						if (providerARN != null) {
							// Update IdP
							UpdateSAMLProviderRequest request = new UpdateSAMLProviderRequest();
							request.withSAMLProviderArn(providerARN);
							request.withSAMLMetadataDocument(Execution.this.readMetadata(metadata));
							UpdateSAMLProviderResult result = iamClient.updateSAMLProvider(request);
							Execution.this.listener.getLogger().format("Updated identity provider %s %n", result.getSAMLProviderArn());
						} else {
							// Create IdP
							CreateSAMLProviderRequest request = new CreateSAMLProviderRequest();
							request.withName(name);
							request.withSAMLMetadataDocument(Execution.this.readMetadata(metadata));
							CreateSAMLProviderResult result = iamClient.createSAMLProvider(request);
							providerARN = result.getSAMLProviderArn();
							Execution.this.listener.getLogger().format("Created identity provider %s %n", providerARN);
						}
						Execution.this.getContext().onSuccess(providerARN);
					} catch (Exception e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}
		
		private String readMetadata(String file) {
			if (file == null) {
				return null;
			}
			
			try {
				return this.workspace.child(file).readToString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		@Override
		public void stop(@Nonnull Throwable cause) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
