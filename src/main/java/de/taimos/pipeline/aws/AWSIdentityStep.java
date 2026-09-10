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
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class AWSIdentityStep extends Step {

	@DataBoundConstructor
	public AWSIdentityStep() {
		//
	}

	@Override
	public StepExecution start(StepContext context) {
		return new AWSIdentityStep.Execution(context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public String getFunctionName() {
			return "awsIdentity";
		}

		@Override
		public String getDisplayName() {
			return "Print and return the AWS identity";
		}

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Map<String, String>> {

		protected Execution(@NonNull StepContext context) {
			super(context);
		}

		@Override
		protected Map<String, String> run() throws Exception {
			StsClient sts = AWSClientFactory.create(StsClient.builder(), this.getContext());
			GetCallerIdentityResponse identity = sts.getCallerIdentity(GetCallerIdentityRequest.builder().build());

			this.getContext().get(TaskListener.class).getLogger().format("Current AWS identity: %s - %s - %s %n", identity.account(), identity.userId(), identity.arn());

			Map<String, String> info = new HashMap<>();
			info.put("account", identity.account());
			info.put("user", identity.userId());
			info.put("arn", identity.arn());
			return info;
		}

		private static final long serialVersionUID = 1L;

	}

}
