/*
 * Copyright (c) 2012, vsc-technologies - www.voyages-sncf.com
 * All rights reserved.
 *
 * Les presents codes sources sont proteges par le droit d'auteur et
 * sont la propriete exclusive de VSC Technologies.
 * Toute representation, reproduction, utilisation, exploitation, modification,
 * adaptation de ces codes sources sont strictement interdits en dehors
 * des autorisations formulees expressement par VSC Technologies,
 * sous peine de poursuites penales.
 *
 * Usage of this software, in source or binary form, partly or in full, and of
 * any application developed with this software, is restricted to the
 * customer.s employees in accordance with the terms of the agreement signed
 * with VSC-technologies.
 */
package de.taimos.pipeline.aws;

import java.io.IOException;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.util.StringUtils;

import de.taimos.pipeline.aws.utils.AssumedRole;
import de.taimos.pipeline.aws.utils.AssumedRole.AssumeRole;
import de.taimos.pipeline.aws.utils.IamRoleUtils;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 */
public class WithRoleStep extends Step {

	private String role = "";
	private String roleAccount = "";
	private String roleSessionName = "";
	private String policy = "";
	private Integer duration = 3600;
	private String externalId = "";
	private String principalArn = "";
	private String samlAssertion = "";

	@DataBoundConstructor
	public WithRoleStep() {
		//
	}

	public String getRole() {
		return role;
	}

	@DataBoundSetter
	public void setRole(final String role) {
		this.role = role;
	}

	public String getRoleAccount() {
		return roleAccount;
	}

	@DataBoundSetter
	public void setRoleAccount(final String roleAccount) {
		this.roleAccount = roleAccount;
	}
	
	public String getRoleSessionName() {
		return roleSessionName;
	}

	@DataBoundSetter
	public void setRoleSessionName(final String roleSessionName) {
		this.roleSessionName = roleSessionName;
	}

	public String getPolicy() {
		return policy;
	}

	@DataBoundSetter
	public void setPolicy(final String policy) {
		this.policy = policy;
	}

	public Integer getDuration() {
		return duration;
	}

	@DataBoundSetter
	public void setDuration(final Integer duration) {
		this.duration = duration;
	}

	public String getExternalId() {
		return externalId;
	}

	@DataBoundSetter
	public void setExternalId(final String externalId) {
		this.externalId = externalId;
	}

	public String getPrincipalArn() {
		return principalArn;
	}

	@DataBoundSetter
	public void setPrincipalArn(final String principalArn) {
		this.principalArn = principalArn;
	}
	
	public String getSamlAssertion() {
		return samlAssertion;
	}

	@DataBoundSetter
	public void setSamlAssertion(final String samlAssertion) {
		this.samlAssertion = samlAssertion;
	}

	@Override
	public StepExecution start(final StepContext context) throws Exception {
		return new WithRoleStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, Run.class);
		}
		
		@Override
		public String getFunctionName() {
			return "withRole";
		}

		@Override
		public String getDisplayName() {
			return "switch AWS role using assume role method";
		}

		@Override
		public boolean takesImplicitBlockArgument() {
			return true;
		}
		
	}

	public static class Execution extends StepExecution {

		private final transient WithRoleStep step;

		private final EnvVars envVars;

		private static final long serialVersionUID = 6995764052251236321L;

		public Execution(WithRoleStep step, StepContext context) {
			super(context);
			this.step = step;
			try {
				this.envVars = context.get(EnvVars.class);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public boolean start() throws Exception {
			final EnvVars awsRoleEnv = new EnvVars();
			assumeRole(awsRoleEnv);
			EnvironmentExpander expander = new EnvironmentExpander() {
				@Override
				public void expand(@Nonnull EnvVars envVars) throws IOException, InterruptedException {
					envVars.overrideAll(awsRoleEnv);
				}
			};
			this.getContext().newBodyInvoker()
					.withContext(EnvironmentExpander.merge(this.getContext().get(EnvironmentExpander.class), expander))
					.withCallback(BodyExecutionCallback.wrap(this.getContext()))
					.start();
			return false;
		}

		private void assumeRole(@Nonnull EnvVars localEnv) throws IOException, InterruptedException {
			AWSSecurityTokenService sts = AWSClientFactory.create(AWSSecurityTokenServiceClientBuilder.standard(), this.envVars);

			AssumeRole assumeRole = IamRoleUtils.validRoleArn(this.step.getRole()) ? new AssumeRole(this.step.getRole()) :
					new AssumeRole(this.step.getRole(), createAccountId(sts), IamRoleUtils.selectPartitionName(this.envVars.get(AWSClientFactory.AWS_REGION, this.envVars.get(AWSClientFactory.AWS_DEFAULT_REGION))));
			assumeRole.withDurationSeconds(this.step.getDuration());
			assumeRole.withExternalId(this.step.getExternalId());
			assumeRole.withPolicy(this.step.getPolicy());
			assumeRole.withSamlAssertion(this.step.getSamlAssertion(), this.step.getPrincipalArn());
			assumeRole.withSessionName(this.createRoleSessionName());

			this.getContext().get(TaskListener.class).getLogger().format("Requesting assume role");
			AssumedRole assumedRole = assumeRole.assumedRole(sts);
			this.getContext().get(TaskListener.class).getLogger().format("Assumed role %s with id %s %n ", assumedRole.getAssumedRoleUser().getArn(), assumedRole.getAssumedRoleUser().getAssumedRoleId());

			localEnv.override(AWSClientFactory.AWS_ACCESS_KEY_ID, assumedRole.getCredentials().getAccessKeyId());
			localEnv.override(AWSClientFactory.AWS_SECRET_ACCESS_KEY, assumedRole.getCredentials().getSecretAccessKey());
			localEnv.override(AWSClientFactory.AWS_SESSION_TOKEN, assumedRole.getCredentials().getSessionToken());
			this.envVars.overrideAll(localEnv);
		}

		private String createAccountId(final AWSSecurityTokenService sts) {
			if (!StringUtils.isNullOrEmpty(this.step.getRoleAccount())) {
				return this.step.getRoleAccount();
			} else {
				return sts.getCallerIdentity(new GetCallerIdentityRequest()).getAccount();
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
	}
}
