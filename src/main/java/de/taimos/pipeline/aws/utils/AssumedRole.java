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
package de.taimos.pipeline.aws.utils;

import java.util.Optional;

import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.amazonaws.services.securitytoken.model.AssumedRoleUser;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.util.StringUtils;

/**
 */
public class AssumedRole {
	
	private final Credentials credentials;
	
	private final AssumedRoleUser assumedRoleUser;

	private AssumedRole(final Credentials credentials, final AssumedRoleUser assumedRoleUser) {
		this.credentials = credentials;
		this.assumedRoleUser = assumedRoleUser;
	}

	public Credentials getCredentials() {
		return this.credentials;
	}

	public AssumedRoleUser getAssumedRoleUser() {
		return this.assumedRoleUser;
	}

	public static class AssumeRole {
		private final String roleArn;
		private String sessionName;
		private String externalId;
		private String policy;
		private Integer durationInSeconds;
		private String samlAssertion;
		private String principalArn;

		public AssumeRole(final String role, final String accountId, final String region) {
			this.roleArn = String.format("arn:%s:iam::%s:role/%s", IamRoleUtils.selectPartitionName(region), accountId, role);
		}

		public AssumeRole(final String roleArn) {
			this.roleArn = roleArn;
		}

		@Override
		public String toString() {
			return this.roleArn;
		}
		
		public AssumeRole withSessionName(final String sessionName) {
			this.sessionName = StringUtils.isNullOrEmpty(sessionName) ? null : sessionName;
			return this;
		}
		
		public AssumeRole withExternalId(final String externalId) {
			this.externalId = StringUtils.isNullOrEmpty(externalId) ? null : externalId;
			return this;
		}
		
		public AssumeRole withPolicy(final String policy) {
			this.policy =  StringUtils.isNullOrEmpty(policy) ? null : policy;
			return this;
		}
		
		public AssumeRole withDurationSeconds(final Integer durationInSeconds) {
			this.durationInSeconds = durationInSeconds;
			return this;
		}
		
		public AssumeRole withSamlAssertion(final String samlAssertion, final String principalArn) {
			this.samlAssertion =  StringUtils.isNullOrEmpty(samlAssertion) ? null : samlAssertion;
			this.principalArn = principalArn;
			return this;
		}
		
		public AssumedRole assumedRole(final AWSSecurityTokenService sts) {
			return this.samlAssertion == null ? this.assumeRole(sts) : this.assumeRoleWithSAML(sts);
		}
		
		private AssumedRole assumeRole(final AWSSecurityTokenService sts) {
			final AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest().withRoleArn(this.roleArn)
							.withRoleSessionName(this.sessionName)
							.withDurationSeconds(this.durationInSeconds);
			Optional.ofNullable(this.externalId).ifPresent(assumeRoleRequest::setExternalId);
			Optional.ofNullable(this.policy).ifPresent(assumeRoleRequest::withPolicy);
			AssumeRoleResult assumeRoleResult = sts.assumeRole(assumeRoleRequest);
			return new AssumedRole(assumeRoleResult.getCredentials(), assumeRoleResult.getAssumedRoleUser());
		}

		private AssumedRole assumeRoleWithSAML(final AWSSecurityTokenService sts) {
			final AssumeRoleWithSAMLRequest assumeRoleRequest = new AssumeRoleWithSAMLRequest().withRoleArn(this.roleArn)
					.withDurationSeconds(this.durationInSeconds)
					.withPrincipalArn(this.principalArn)
					.withSAMLAssertion(this.samlAssertion);
			Optional.ofNullable(this.policy).ifPresent(assumeRoleRequest::withPolicy);
			AssumeRoleWithSAMLResult assumeRoleWithSAMLResult = sts.assumeRoleWithSAML(assumeRoleRequest);
			return new AssumedRole(assumeRoleWithSAMLResult.getCredentials(), assumeRoleWithSAMLResult.getAssumedRoleUser());
		}
		
	}
}
