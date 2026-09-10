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

import edu.umd.cs.findbugs.annotations.NonNull;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetFederationTokenRequest;
import software.amazon.awssdk.services.sts.model.GetFederationTokenResponse;

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
				public void expand(@NonNull EnvVars envVars) {
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

		private void withFederatedUserId(@NonNull EnvVars localEnv) {
			if (this.step.getFederatedUserId() != null && !this.step.getFederatedUserId().isEmpty()) {
				StsClient sts = AWSClientFactory.create(StsClient.builder(), this.getContext(), this.envVars);
				GetFederationTokenRequest getFederationTokenRequest = GetFederationTokenRequest.builder()
						.durationSeconds(this.step.getDuration())
						.name(this.step.getFederatedUserId())
						.policy(ALLOW_ALL_POLICY)
						.build();

				GetFederationTokenResponse federationTokenResult = sts.getFederationToken(getFederationTokenRequest);

				Credentials credentials = federationTokenResult.credentials();
				localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, credentials.accessKeyId());
				localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, credentials.secretAccessKey());
				localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, credentials.sessionToken());
				this.envVars.overrideAll(localEnv);
			}

		}

		/**
		 * Every branch that installs a key and secret has to drop an inherited AWS_SESSION_TOKEN with
		 * them. Pairing new credentials with a token belonging to something else does not sign, so
		 * withAWS(credentials: ...) nested inside withAWS(role: ...) would fail every nested call.
		 *
		 * put, not override: EnvVars.override treats an empty value as "remove this key", so override
		 * here would drop the entry from the overlay and let the outer value show through unchanged.
		 * Carrying an empty value instead makes the expander's own overrideAll remove it from the
		 * body's environment, which is what is wanted.
		 *
		 * A token supplied deliberately out of band - from withEnv, a global, or a credentials binding
		 * - is dropped too, because the step cannot tell that apart from a stale one. Logged so the
		 * resulting 403s are diagnosable rather than mysterious.
		 *
		 * Not logged when role or federatedUserId is set: start() runs those stages after this one and
		 * both install a token of their own, so the block ends up with a valid one and the message
		 * would be a false alarm - including for the only documented SAML usage, which pairs the
		 * assertion with a role.
		 */
		private void clearInheritedSessionToken(@NonNull EnvVars localEnv) throws IOException, InterruptedException {
			boolean aLaterStageSuppliesOne = (this.step.getRole() != null && !this.step.getRole().isEmpty())
					|| (this.step.getFederatedUserId() != null && !this.step.getFederatedUserId().isEmpty());
			String inheritedToken = this.envVars.get(AWSClientFactory.AWS_SESSION_TOKEN);
			if (!aLaterStageSuppliesOne && inheritedToken != null && !inheritedToken.isEmpty()) {
				this.getContext().get(TaskListener.class).getLogger().println(
						"Dropping the inherited AWS_SESSION_TOKEN: these credentials do not carry one");
			}
			localEnv.put(AWSClientFactory.AWS_SESSION_TOKEN, "");
		}

		private void withCredentials(@NonNull Run<?, ?> run, @NonNull EnvVars localEnv) throws IOException, InterruptedException {
			if (this.step.getCredentials() != null && !this.step.getCredentials().isEmpty()) {
				StandardUsernamePasswordCredentials usernamePasswordCredentials = CredentialsProvider.findCredentialById(this.step.getCredentials(),
						StandardUsernamePasswordCredentials.class, run, Collections.emptyList());

				AmazonWebServicesCredentials amazonWebServicesCredentials = CredentialsProvider.findCredentialById(this.step.getCredentials(),
						AmazonWebServicesCredentials.class, run, Collections.emptyList());
				if (usernamePasswordCredentials != null) {
					localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, usernamePasswordCredentials.getUsername());
					localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, usernamePasswordCredentials.getPassword().getPlainText());
					this.clearInheritedSessionToken(localEnv);
				} else if (amazonWebServicesCredentials != null) {
					AwsCredentials awsCredentials;

					if (this.step.getIamMfaToken() == null || this.step.getIamMfaToken().isEmpty()) {
						this.getContext().get(TaskListener.class).getLogger().format("Constructing AWS Credentials");
						awsCredentials = amazonWebServicesCredentials.resolveCredentials();
					} else {
						// Since resolveCredentials does its own roleAssumption, this is all it takes to get credentials
						// with this token.
						this.getContext().get(TaskListener.class).getLogger().format("Constructing AWS Credentials utilizing MFA Token");
						awsCredentials = amazonWebServicesCredentials.resolveCredentials(this.step.getIamMfaToken());
					}

					// v1 cast to BasicSessionCredentials on the MFA branch only. v2 collapses v1's
					// BasicSessionCredentials and STSSessionCredentials into AwsSessionCredentials, so
					// this tests the type instead - which also covers a session token arriving on the
					// non-MFA branch, where v1 would have dropped it. The test is against the interface
					// rather than the built-in implementation, because AmazonWebServicesCredentials is
					// an extension point and another implementation's session credential would
					// otherwise have its token dropped silently, leaving a pair that cannot sign.
					if (awsCredentials instanceof AwsSessionCredentialsIdentity) {
						localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, ((AwsSessionCredentialsIdentity) awsCredentials).sessionToken());
					} else {
						this.clearInheritedSessionToken(localEnv);
					}

					localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, awsCredentials.accessKeyId());
					localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, awsCredentials.secretAccessKey());
				} else {
					throw new RuntimeException("Cannot find a Username with password credential with the ID " + this.step.getCredentials());
				}
			} else if (this.step.getSamlAssertion() != null && !this.step.getSamlAssertion().isEmpty()) {
				localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, "access_key_not_used_will_pass_through_SAML_assertion");
				localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret_access_key_not_used_will_pass_through_SAML_assertion");
				// Placeholders rather than real keys, so a stale token alongside them is no more valid.
				// It does not affect the AssumeRoleWithSAML call itself - removing this clear leaves the
				// STS error unchanged - but it keeps the body from inheriting one.
				this.clearInheritedSessionToken(localEnv);
			}
			this.envVars.overrideAll(localEnv);
		}

		private void withRole(@NonNull EnvVars localEnv) throws IOException, InterruptedException {
			if (this.step.getRole() != null && !this.step.getRole().isEmpty()) {

				StsClient sts = AWSClientFactory.create(StsClient.builder(), this.getContext(), this.envVars);

				AssumeRole assumeRole = IamRoleUtils.validRoleArn(this.step.getRole()) ? new AssumeRole(this.step.getRole()) :
						new AssumeRole(this.step.getRole(), this.createAccountId(sts), this.step.getRegion());
				assumeRole.withDurationSeconds(this.step.getDuration());
				assumeRole.withExternalId(this.step.getExternalId());
				assumeRole.withPolicy(this.step.getPolicy());
				assumeRole.withSamlAssertion(this.step.getSamlAssertion(), this.step.getPrincipalArn());
				assumeRole.withSessionName(this.createRoleSessionName());

				this.getContext().get(TaskListener.class).getLogger().format("Requesting assume role%n");
				this.getContext().get(TaskListener.class).getLogger().format("Assuming role ARN is %s", assumeRole.toString());
				AssumedRole assumedRole = assumeRole.assumedRole(sts);
				this.getContext().get(TaskListener.class).getLogger().format("Assumed role %s with id %s %n ", assumedRole.getAssumedRoleUser().arn(), assumedRole.getAssumedRoleUser().assumedRoleId());

				localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, assumedRole.getCredentials().accessKeyId());
				localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, assumedRole.getCredentials().secretAccessKey());
				localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, assumedRole.getCredentials().sessionToken());
				this.envVars.overrideAll(localEnv);
			}
		}

		private void withRegion(@NonNull EnvVars localEnv) throws IOException, InterruptedException {
			if (this.step.getRegion() != null && !this.step.getRegion().isEmpty()) {
				this.getContext().get(TaskListener.class).getLogger().format("Setting AWS region %s %n ", this.step.getRegion());
				localEnv.override(AWSClientFactory.AWS_DEFAULT_REGION, this.step.getRegion());
				localEnv.override(AWSClientFactory.AWS_REGION, this.step.getRegion());
				this.envVars.overrideAll(localEnv);
			}
		}

		private void withEndpointUrl(@NonNull EnvVars localEnv) throws IOException, InterruptedException {
			if (this.step.getEndpointUrl() != null && !this.step.getEndpointUrl().isEmpty()) {
				this.getContext().get(TaskListener.class).getLogger().format("Setting AWS endpointUrl %s %n ", this.step.getEndpointUrl());
				localEnv.override(AWSClientFactory.AWS_ENDPOINT_URL, this.step.getEndpointUrl());
				this.envVars.overrideAll(localEnv);
			}
		}

		private void withProfile(@NonNull EnvVars localEnv) throws IOException, InterruptedException {
			if (this.step.getProfile() != null && !this.step.getProfile().isEmpty()) {
				this.getContext().get(TaskListener.class).getLogger().format("Setting AWS profile %s %n ", this.step.getProfile());
				localEnv.override(AWSClientFactory.AWS_DEFAULT_PROFILE, this.step.getProfile());
				localEnv.override(AWSClientFactory.AWS_PROFILE, this.step.getProfile());
				this.envVars.overrideAll(localEnv);
			}
		}

		private String createRoleSessionName() {
			if (this.step.roleSessionName == null || this.step.roleSessionName.isEmpty()) {
				return RoleSessionNameBuilder
						.withJobName(this.envVars.get("JOB_NAME"))
						.withBuildNumber(this.envVars.get("BUILD_NUMBER"))
						.build();
			} else {
				return this.step.roleSessionName;
			}
		}

		private String createAccountId(final StsClient sts) {
			if (this.step.getRoleAccount() != null && !this.step.getRoleAccount().isEmpty()) {
				return this.step.getRoleAccount();
			} else {
				return sts.getCallerIdentity(GetCallerIdentityRequest.builder().build()).account();
			}
		}

		private static final long serialVersionUID = 1L;

	}

}
