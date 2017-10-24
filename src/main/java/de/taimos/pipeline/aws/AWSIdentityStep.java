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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class AWSIdentityStep extends AbstractStepImpl {

	@DataBoundConstructor
	public AWSIdentityStep() {
		//
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(Execution.class);
		}

		@Override
		public String getFunctionName() {
			return "awsIdentity";
		}

		@Override
		public String getDisplayName() {
			return "Print and return the AWS identity";
		}
	}

	public static class Execution extends AbstractSynchronousStepExecution<Map<String, String>> {

		@Inject
		private transient AWSIdentityStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;

		@Override
		protected Map<String, String> run() throws Exception {
			AWSSecurityTokenService sts = AWSClientFactory.createAWSSecurityTokenServiceClient(this.envVars);
			GetCallerIdentityResult identity = sts.getCallerIdentity(new GetCallerIdentityRequest());

			this.listener.getLogger().format("Current AWS identity: %s - %s - %s %n", identity.getAccount(), identity.getUserId(), identity.getArn());

			Map<String, String> info = new HashMap<>();
			info.put("account", identity.getAccount());
			info.put("user", identity.getUserId());
			info.put("arn", identity.getArn());
			return info;
		}

		private static final long serialVersionUID = 1L;

	}

}
