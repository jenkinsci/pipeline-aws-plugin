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
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenResult;
import com.amazonaws.util.StringUtils;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import de.taimos.pipeline.aws.utils.AssumedRole;
import de.taimos.pipeline.aws.utils.AssumedRole.AssumeRole;
import de.taimos.pipeline.aws.utils.IamRoleUtils;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.ListBoxModel;

public class WithAWSStep extends Step {

	private String role = "";
	private String roleAccount = "";
	private String region = "";
	private String endpointUrl = "";
	private String profile = "";
	private String credentials = "";
	private String externalId = "";
	private String federatedUserId = "";
	private String policy = "";
	private String iamMfaToken = "";
	private Integer duration = 3600;
	private String roleSessionName;
	private String principalArn = "";
	private String samlAssertion = "";
	private boolean useNode = false;

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

	public String getEndpointUrl() {
		return this.endpointUrl;
	}

	@DataBoundSetter
	public void setEndpointUrl(String endpointUrl) {
		this.endpointUrl = endpointUrl;
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

	public String getIamMfaToken() {
		return this.iamMfaToken;
	}

	@DataBoundSetter
	public void setIamMfaToken(String iamMfaToken) {
		this.iamMfaToken = iamMfaToken;
	}

	public String getFederatedUserId() {
		return this.federatedUserId;
	}

	@DataBoundSetter
	public void setFederatedUserId(String federatedUserId) {
		this.federatedUserId = federatedUserId;
	}

	public String getPolicy() {
		return this.policy;
	}

	@DataBoundSetter
	public void setPolicy(String policy) {
		this.policy = policy;
	}

	public Integer getDuration() {
		return this.duration;
	}

	@DataBoundSetter
	public void setDuration(Integer duration) {
		this.duration = duration;
	}

	public String getRoleSessionName() {
		return this.roleSessionName;
	}

	@DataBoundSetter
	public void setRoleSessionName(String roleSessionName) {
		this.roleSessionName = roleSessionName;
	}

	public String getPrincipalArn() {
		return this.principalArn;
	}

	@DataBoundSetter
	public void setPrincipalArn(final String principalArn) {
		this.principalArn = principalArn;
	}

	public String getSamlAssertion() {
		return this.samlAssertion;
	}

	@DataBoundSetter
	public void setSamlAssertion(final String samlAssertion) {
		this.samlAssertion = samlAssertion;
	}

	public boolean getUseNode() {
		return this.useNode;
	}

	@DataBoundSetter
	public void setUseNode(final boolean useNode) {
		this.useNode = useNode;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new WithAWSStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, Run.class);
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
							Collections.emptyList(),
							CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class))
					.includeMatchingAs(context instanceof Queue.Task
									? Tasks.getAuthenticationOf((Queue.Task) context)
									: ACL.SYSTEM,
							context,
							AmazonWebServicesCredentials.class,
							Collections.emptyList(),
							CredentialsMatchers.instanceOf(AmazonWebServicesCredentials.class));
		}
	}

	public static class Execution extends StepExecution {

		private final transient WithAWSStep step;

		private final EnvVars envVars;

		public Execution(WithAWSStep step, StepContext context) {
			super(context);
			this.step = step;
			try {
				this.envVars = context.get(EnvVars.class);
				this.envVars.put(AWSClientFactory.AWS_PIPELINE_STEPS_FROM_NODE, String.valueOf(this.step.getUseNode()));
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public boolean start() throws Exception {
			final EnvVars awsEnv = new EnvVars();
			this.withCredentials(this.getContext().get(Run.class), awsEnv);
			this.withProfile(awsEnv);
			this.withRegion(awsEnv);
			this.withEndpointUrl(awsEnv);
			this.withRole(awsEnv);
			this.withFederatedUserId(awsEnv);

			EnvironmentExpander expander = new EnvironmentExpander() {
				private static final long serialVersionUID = 1L;
				@Override
				public void expand(@Nonnull EnvVars envVars) {
					envVars.overrideAll(awsEnv);
				}
			};
			this.getContext().newBodyInvoker()
					.withContext(EnvironmentExpander.merge(this.getContext().get(EnvironmentExpander.class), expander))
					.withCallback(BodyExecutionCallback.wrap(this.getContext()))
					.start();
			return false;
		}

		private static final String ALLOW_ALL_POLICY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Action\":\"*\","
				+ "\"Effect\":\"Allow\",\"Resource\":\"*\"}]}";

		private void withFederatedUserId(@Nonnull EnvVars localEnv) {
			if (!StringUtils.isNullOrEmpty(this.step.getFederatedUserId())) {
				AWSSecurityTokenService sts = AWSClientFactory.create(AWSSecurityTokenServiceClientBuilder.standard(), this.getContext(), this.envVars);
				GetFederationTokenRequest getFederationTokenRequest = new GetFederationTokenRequest();
				getFederationTokenRequest.setDurationSeconds(this.step.getDuration());
				getFederationTokenRequest.setName(this.step.getFederatedUserId());
				getFederationTokenRequest.setPolicy(ALLOW_ALL_POLICY);

				GetFederationTokenResult federationTokenResult = sts.getFederationToken(getFederationTokenRequest);

				Credentials credentials = federationTokenResult.getCredentials();
				localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, credentials.getAccessKeyId());
				localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, credentials.getSecretAccessKey());
				localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, credentials.getSessionToken());
				this.envVars.overrideAll(localEnv);
			}

		}

		private void withCredentials(@Nonnull Run<?, ?> run, @Nonnull EnvVars localEnv) throws IOException, InterruptedException {
			if (!StringUtils.isNullOrEmpty(this.step.getCredentials())) {
				StandardUsernamePasswordCredentials usernamePasswordCredentials = CredentialsProvider.findCredentialById(this.step.getCredentials(),
						StandardUsernamePasswordCredentials.class, run, Collections.emptyList());

				AmazonWebServicesCredentials amazonWebServicesCredentials = CredentialsProvider.findCredentialById(this.step.getCredentials(),
						AmazonWebServicesCredentials.class, run, Collections.emptyList());
				if (usernamePasswordCredentials != null) {
					localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, usernamePasswordCredentials.getUsername());
					localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, usernamePasswordCredentials.getPassword().getPlainText());
				} else if (amazonWebServicesCredentials != null) {
					AWSCredentials awsCredentials;

					if (StringUtils.isNullOrEmpty(this.step.getIamMfaToken())) {
						this.getContext().get(TaskListener.class).getLogger().format("Constructing AWS Credentials");
						awsCredentials = amazonWebServicesCredentials.getCredentials();
					} else {
						// Since the getCredentials does its own roleAssumption, this is all it takes to get credentials
						// with this token.
						this.getContext().get(TaskListener.class).getLogger().format("Constructing AWS Credentials utilizing MFA Token");
						awsCredentials = amazonWebServicesCredentials.getCredentials(this.step.getIamMfaToken());
						BasicSessionCredentials basicSessionCredentials = (BasicSessionCredentials) awsCredentials;
						localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, basicSessionCredentials.getSessionToken());
					}

					localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, awsCredentials.getAWSAccessKeyId());
					localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, awsCredentials.getAWSSecretKey());
				} else {
					throw new RuntimeException("Cannot find a Username with password credential with the ID " + this.step.getCredentials());
				}
			} else if (!StringUtils.isNullOrEmpty(this.step.getSamlAssertion())) {
				localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, "access_key_not_used_will_pass_through_SAML_assertion");
				localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret_access_key_not_used_will_pass_through_SAML_assertion");
			}
			this.envVars.overrideAll(localEnv);
		}

		private void withRole(@Nonnull EnvVars localEnv) throws IOException, InterruptedException {
			if (!StringUtils.isNullOrEmpty(this.step.getRole())) {

				AWSSecurityTokenService sts = AWSClientFactory.create(AWSSecurityTokenServiceClientBuilder.standard(), this.getContext(), this.envVars);

				AssumeRole assumeRole = IamRoleUtils.validRoleArn(this.step.getRole()) ? new AssumeRole(this.step.getRole()) :
						new AssumeRole(this.step.getRole(), this.createAccountId(sts), this.step.getRegion());
				assumeRole.withDurationSeconds(this.step.getDuration());
				assumeRole.withExternalId(this.step.getExternalId());
				assumeRole.withPolicy(this.step.getPolicy());
				assumeRole.withSamlAssertion(this.step.getSamlAssertion(), this.step.getPrincipalArn());
				assumeRole.withSessionName(this.createRoleSessionName());

				this.getContext().get(TaskListener.class).getLogger().format("Requesting assume role\n");
				this.getContext().get(TaskListener.class).getLogger().format("Assuming role ARN is %s", assumeRole.toString());
				AssumedRole assumedRole = assumeRole.assumedRole(sts);
				this.getContext().get(TaskListener.class).getLogger().format("Assumed role %s with id %s %n ", assumedRole.getAssumedRoleUser().getArn(), assumedRole.getAssumedRoleUser().getAssumedRoleId());

				localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, assumedRole.getCredentials().getAccessKeyId());
				localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, assumedRole.getCredentials().getSecretAccessKey());
				localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, assumedRole.getCredentials().getSessionToken());
				this.envVars.overrideAll(localEnv);
			}
		}

		private void withRegion(@Nonnull EnvVars localEnv) throws IOException, InterruptedException {
			if (!StringUtils.isNullOrEmpty(this.step.getRegion())) {
				this.getContext().get(TaskListener.class).getLogger().format("Setting AWS region %s %n ", this.step.getRegion());
				localEnv.override(AWSClientFactory.AWS_DEFAULT_REGION, this.step.getRegion());
				localEnv.override(AWSClientFactory.AWS_REGION, this.step.getRegion());
				this.envVars.overrideAll(localEnv);
			}
		}

		private void withEndpointUrl(@Nonnull EnvVars localEnv) throws IOException, InterruptedException {
			if (!StringUtils.isNullOrEmpty(this.step.getEndpointUrl())) {
				this.getContext().get(TaskListener.class).getLogger().format("Setting AWS endpointUrl %s %n ", this.step.getEndpointUrl());
				localEnv.override(AWSClientFactory.AWS_ENDPOINT_URL, this.step.getEndpointUrl());
				this.envVars.overrideAll(localEnv);
			}
		}

		private void withProfile(@Nonnull EnvVars localEnv) throws IOException, InterruptedException {
			if (!StringUtils.isNullOrEmpty(this.step.getProfile())) {
				this.getContext().get(TaskListener.class).getLogger().format("Setting AWS profile %s %n ", this.step.getProfile());
				localEnv.override(AWSClientFactory.AWS_DEFAULT_PROFILE, this.step.getProfile());
				localEnv.override(AWSClientFactory.AWS_PROFILE, this.step.getProfile());
				this.envVars.overrideAll(localEnv);
			}
		}

		private String createRoleSessionName() {
			if (StringUtils.isNullOrEmpty(this.step.roleSessionName)) {
				return RoleSessionNameBuilder
						.withJobName(this.envVars.get("JOB_NAME"))
						.withBuildNumber(this.envVars.get("BUILD_NUMBER"))
						.build();
			} else {
				return this.step.roleSessionName;
			}
		}

		private String createAccountId(final AWSSecurityTokenService sts) {
			if (!StringUtils.isNullOrEmpty(this.step.getRoleAccount())) {
				return this.step.getRoleAccount();
			} else {
				return sts.getCallerIdentity(new GetCallerIdentityRequest()).getAccount();
			}
		}

		private static final long serialVersionUID = 1L;

	}

}
