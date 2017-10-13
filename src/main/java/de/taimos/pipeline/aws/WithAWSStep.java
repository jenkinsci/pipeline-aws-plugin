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

import java.io.IOException;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenResult;
import com.amazonaws.util.StringUtils;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import de.taimos.pipeline.aws.utils.IamRoleUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.ListBoxModel;

public class WithAWSStep extends AbstractStepImpl {
	
	private String role = "";
	private String roleAccount = "";
	private String region = "";
	private String profile = "";
	private String credentials = "";
	private String externalId = "";
	private String federatedUserId = "";
	
	@DataBoundConstructor
	public WithAWSStep() {
		//
	}
	
	public String getRole() {
		return this.role;
	}
	
	@DataBoundSetter
	public void setRole(String role) {
		this.role = role;
	}
	
	public String getRoleAccount() {
		return this.roleAccount;
	}
	
	@DataBoundSetter
	public void setRoleAccount(String roleAccount) {
		this.roleAccount = roleAccount;
	}
	
	public String getRegion() {
		return this.region;
	}
	
	@DataBoundSetter
	public void setRegion(String region) {
		this.region = region;
	}
	
	public String getProfile() {
		return this.profile;
	}
	
	@DataBoundSetter
	public void setProfile(String profile) {
		this.profile = profile;
	}
	
	public String getCredentials() {
		return this.credentials;
	}
	
	@DataBoundSetter
	public void setCredentials(String credentials) {
		this.credentials = credentials;
	}
	
	public String getExternalId() {
		return this.externalId;
	}
	
	@DataBoundSetter
	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}
	
	public String getFederatedUserId() {
		return this.federatedUserId;
	}
	
	@DataBoundSetter
	public void setFederatedUserId(String federatedUserId) {
		this.federatedUserId = federatedUserId;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "withAWS";
		}
		
		@Override
		public String getDisplayName() {
			return "set AWS settings for nested block";
		}
		
		@Override
		public boolean takesImplicitBlockArgument() {
			return true;
		}
		
		public ListBoxModel doFillCredentialsItems(@AncestorInPath Item context) {
			
			if (context == null || !context.hasPermission(Item.CONFIGURE)) {
				return new ListBoxModel();
			}
			
			return new StandardListBoxModel()
					.includeEmptyValue()
					.includeMatchingAs(
							context instanceof Queue.Task
									? Tasks.getAuthenticationOf((Queue.Task) context)
									: ACL.SYSTEM,
							context,
							StandardUsernamePasswordCredentials.class,
							Collections.<DomainRequirement>emptyList(),
							CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient WithAWSStep step;
		@StepContextParameter
		private transient TaskListener listener;
		@StepContextParameter
		private transient EnvVars envVars;
		
		@Override
		public boolean start() throws Exception {
			final EnvVars awsEnv = new EnvVars();
			this.withCredentials(getContext().get(Run.class), awsEnv);
			this.withProfile(awsEnv);
			this.withRegion(awsEnv);
			this.withRole(awsEnv);
			this.withFederatedUserId(awsEnv);
			
			EnvironmentExpander expander = new EnvironmentExpander() {
				@Override
				public void expand(@Nonnull EnvVars envVars) throws IOException, InterruptedException {
					envVars.overrideAll(awsEnv);
				}
			};
			this.getContext().newBodyInvoker()
					.withContext(EnvironmentExpander.merge(this.getContext().get(EnvironmentExpander.class), expander))
					.withCallback(BodyExecutionCallback.wrap(this.getContext()))
					.start();
			return false;
		}
		
		private final String ALLOW_ALL_POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Action\":\"*\","
				+ "\"Effect\":\"Allow\",\"Resource\":\"*\"}]}";
		
		private void withFederatedUserId(@Nonnull EnvVars localEnv) {
			if (!StringUtils.isNullOrEmpty(this.step.getFederatedUserId())) {
				AWSSecurityTokenServiceClient sts = AWSClientFactory.create(AWSSecurityTokenServiceClient.class, this.envVars);
				
				GetFederationTokenRequest getFederationTokenRequest = new GetFederationTokenRequest();
				getFederationTokenRequest.setDurationSeconds(3600);
				getFederationTokenRequest.setName(this.step.getFederatedUserId());
				getFederationTokenRequest.setPolicy(this.ALLOW_ALL_POLICY);
				
				GetFederationTokenResult federationTokenResult = sts.getFederationToken(getFederationTokenRequest);
				
				Credentials credentials = federationTokenResult.getCredentials();
				localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, credentials.getAccessKeyId());
				localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, credentials.getSecretAccessKey());
				localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, credentials.getSessionToken());
				this.envVars.overrideAll(localEnv);
			}
			
		}
		
		private void withCredentials(@Nonnull Run<?, ?> run, @Nonnull EnvVars localEnv) {
			if (!StringUtils.isNullOrEmpty(this.step.getCredentials())) {
				StandardUsernamePasswordCredentials usernamePasswordCredentials = CredentialsProvider.findCredentialById(this.step.getCredentials(),
																														 StandardUsernamePasswordCredentials.class, run, Collections.<DomainRequirement>emptyList());
				if (usernamePasswordCredentials != null) {
					localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, usernamePasswordCredentials.getUsername());
					localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, usernamePasswordCredentials.getPassword().getPlainText());
					this.envVars.overrideAll(localEnv);
				} else {
					throw new RuntimeException("Cannot find Jenkins credentials with name " + this.step.getCredentials());
				}
			}
		}
		
		private void withRole(@Nonnull EnvVars localEnv) {
			if (!StringUtils.isNullOrEmpty(this.step.getRole())) {
				AWSSecurityTokenServiceClient sts = AWSClientFactory.create(AWSSecurityTokenServiceClient.class, this.envVars);
				
				final String accountId;
				if (!StringUtils.isNullOrEmpty(this.step.getRoleAccount())) {
					accountId = this.step.getRoleAccount();
				} else {
					accountId = sts.getCallerIdentity(new GetCallerIdentityRequest()).getAccount();
				}
				
				String roleARN = IamRoleUtils.validRoleArn(this.step.getRole()) ? this.step.getRole() : String.format("arn:%s:iam::%s:role/%s", IamRoleUtils.selectPartitionName(this.step.getRegion()), accountId, this.step.getRole());
				
				AssumeRoleRequest request = new AssumeRoleRequest()
						.withRoleArn(roleARN)
						.withRoleSessionName(this.createRoleSessionName());
				if (!StringUtils.isNullOrEmpty(this.step.getExternalId())) {
					request.withExternalId(this.step.getExternalId());
				}
				
				AssumeRoleResult assumeRole = sts.assumeRole(request);
				
				this.listener.getLogger().format("Assumed role %s with id %s %n ", roleARN, assumeRole.getAssumedRoleUser().getAssumedRoleId());
				
				Credentials credentials = assumeRole.getCredentials();
				localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, credentials.getAccessKeyId());
				localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, credentials.getSecretAccessKey());
				localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, credentials.getSessionToken());
				this.envVars.overrideAll(localEnv);
			}
		}
		
		private void withRegion(@Nonnull EnvVars localEnv) {
			if (!StringUtils.isNullOrEmpty(this.step.getRegion())) {
				this.listener.getLogger().format("Setting AWS region %s %n ", this.step.getRegion());
				localEnv.override(AWSClientFactory.AWS_DEFAULT_REGION, this.step.getRegion());
				localEnv.override(AWSClientFactory.AWS_REGION, this.step.getRegion());
				this.envVars.overrideAll(localEnv);
			}
		}
		
		private void withProfile(@Nonnull EnvVars localEnv) {
			if (!StringUtils.isNullOrEmpty(this.step.getProfile())) {
				this.listener.getLogger().format("Setting AWS profile %s %n ", this.step.getProfile());
				localEnv.override(AWSClientFactory.AWS_DEFAULT_PROFILE, this.step.getProfile());
				localEnv.override(AWSClientFactory.AWS_PROFILE, this.step.getProfile());
				this.envVars.overrideAll(localEnv);
			}
		}
		
		private String createRoleSessionName() {
			return RoleSessionNameBuilder
					.withJobName(this.envVars.get("JOB_NAME"))
					.withBuildNumber(this.envVars.get("BUILD_NUMBER"))
					.build();
		}
		
		@Override
		public void stop(@Nonnull Throwable throwable) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
