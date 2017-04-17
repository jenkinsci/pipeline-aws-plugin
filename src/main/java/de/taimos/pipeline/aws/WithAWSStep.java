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
import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.util.StringUtils;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.security.ACL;
import jenkins.model.Jenkins;

public class WithAWSStep extends AbstractStepImpl {
	
	private String role;
	private String roleAccount;
	private String region;
	private String profile;
	private String credentials;
	private String externalId;

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
		return externalId;
	}

	@DataBoundSetter
	public void setExternalId(String externalId) {
		this.externalId = externalId;
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
			this.withCredentials(awsEnv);
			this.withProfile(awsEnv);
			this.withRegion(awsEnv);
			this.withRole(awsEnv);
			
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
		
		private void withCredentials(@Nonnull EnvVars localEnv) {
			if (!StringUtils.isNullOrEmpty(this.step.getCredentials())) {
				List<UsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList());
				CredentialsMatcher matcher = CredentialsMatchers.withId(this.step.getCredentials());
				UsernamePasswordCredentials usernamePasswordCredentials = CredentialsMatchers.firstOrNull(credentials, matcher);
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
				
				String roleARN = String.format("arn:aws:iam::%s:role/%s", accountId, this.step.getRole());
				
				AssumeRoleRequest request = new AssumeRoleRequest().withRoleArn(roleARN).withRoleSessionName("Jenkins-" + System.currentTimeMillis()).withExternalId(this.step.getExternalId());

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
		
		@Override
		public void stop(@Nonnull Throwable throwable) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
