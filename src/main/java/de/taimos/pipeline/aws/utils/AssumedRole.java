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

import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithSamlRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleWithSamlResponse;
import software.amazon.awssdk.services.sts.model.AssumedRoleUser;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.StsClient;

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
			this.sessionName = StringUtils.isEmpty(sessionName) ? null : sessionName;
			return this;
		}
		
		public AssumeRole withExternalId(final String externalId) {
			this.externalId = StringUtils.isEmpty(externalId) ? null : externalId;
			return this;
		}
		
		public AssumeRole withPolicy(final String policy) {
			this.policy =  StringUtils.isEmpty(policy) ? null : policy;
			return this;
		}
		
		public AssumeRole withDurationSeconds(final Integer durationInSeconds) {
			this.durationInSeconds = durationInSeconds;
			return this;
		}
		
		public AssumeRole withSamlAssertion(final String samlAssertion, final String principalArn) {
			this.samlAssertion =  StringUtils.isEmpty(samlAssertion) ? null : samlAssertion;
			this.principalArn = principalArn;
			return this;
		}
		
		public AssumedRole assumedRole(final StsClient sts) {
			return this.samlAssertion == null ? this.assumeRole(sts) : this.assumeRoleWithSAML(sts);
		}
		
		private AssumedRole assumeRole(final StsClient sts) {
			final AssumeRoleRequest.Builder assumeRoleRequest = AssumeRoleRequest.builder().roleArn(this.roleArn)
							.roleSessionName(this.sessionName)
							.durationSeconds(this.durationInSeconds);
			Optional.ofNullable(this.externalId).ifPresent(assumeRoleRequest::externalId);
			Optional.ofNullable(this.policy).ifPresent(assumeRoleRequest::policy);
			AssumeRoleResponse assumeRoleResult = sts.assumeRole(assumeRoleRequest.build());
			return new AssumedRole(assumeRoleResult.credentials(), assumeRoleResult.assumedRoleUser());
		}

		private AssumedRole assumeRoleWithSAML(final StsClient sts) {
			final AssumeRoleWithSamlRequest.Builder assumeRoleRequest = AssumeRoleWithSamlRequest.builder().roleArn(this.roleArn)
					.durationSeconds(this.durationInSeconds)
					.principalArn(this.principalArn)
					.samlAssertion(this.samlAssertion);
			Optional.ofNullable(this.policy).ifPresent(assumeRoleRequest::policy);
			AssumeRoleWithSamlResponse assumeRoleWithSAMLResult = sts.assumeRoleWithSAML(assumeRoleRequest.build());
			return new AssumedRole(assumeRoleWithSAMLResult.credentials(), assumeRoleWithSAMLResult.assumedRoleUser());
		}
		
	}
}
