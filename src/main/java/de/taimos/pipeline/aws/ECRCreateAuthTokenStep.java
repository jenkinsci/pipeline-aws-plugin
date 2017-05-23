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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecr.AmazonECRClient;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

public class ECRCreateAuthTokenStep extends AbstractStepImpl {

	private final String regionName;

	@DataBoundConstructor
	public ECRCreateAuthTokenStep(String regionName) {
		this.regionName = regionName;
	}

	public String getRegionName() {
		return this.regionName;
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "ecrCreateAuthToken";
		}
		
		@Override
		public String getDisplayName() {
			return "Creates an ECR Repository";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient ECRCreateAuthTokenStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final FilePath filePath = this.workspace.child("dockerAuth");
			final String regionName = this.step.getRegionName();

			new Thread("ecrCreateAuthToken") {
				@Override
				public void run() {
					try {
						Execution.this.listener.getLogger().println("Creating Authorization Token");
						final AmazonECRClient ecrClient = AWSClientFactory.create(AmazonECRClient.class, envVars);
						if(null != regionName && !regionName.isEmpty()) {
							ecrClient.setRegion(Region.getRegion(Regions.fromName(regionName)));
						}
						final GetAuthorizationTokenResult tokenResult = ecrClient.getAuthorizationToken(new GetAuthorizationTokenRequest());
						final String token = tokenResult.getAuthorizationData().get(0).getAuthorizationToken();
						final String proxyEndpoint = tokenResult.getAuthorizationData().get(0).getProxyEndpoint();
						filePath.act(new RemoteFileCreator(Execution.this.envVars, Execution.this.listener, token, proxyEndpoint));
						Execution.this.listener.getLogger().format("Authorization Token stored in file dockerAuth");
						Execution.this.getContext().onSuccess(null);
					} catch (Exception e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}
		
		@Override
		public void stop(@Nonnull Throwable cause) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}

	private static class RemoteFileCreator implements FilePath.FileCallable<Void> {

		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String token;
		private final String proxyEndpoint;

		RemoteFileCreator(EnvVars envVars, TaskListener taskListener, String token, String proxyEndpoint) {
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.token = token;
			this.proxyEndpoint = proxyEndpoint;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			StringBuffer stringBuffer = new StringBuffer("login -u AWS -p ");
			final String authToken = StringUtils.newStringUtf8(Base64.decodeBase64(this.token)).split(":")[1];
			stringBuffer.append(authToken);
			stringBuffer.append(" -e none ");
			stringBuffer.append(this.proxyEndpoint);
			FileUtils.writeStringToFile(localFile, stringBuffer.toString());
			return null;
		}

		@Override
		public void checkRoles(RoleChecker roleChecker) throws SecurityException {

		}
	}
}
