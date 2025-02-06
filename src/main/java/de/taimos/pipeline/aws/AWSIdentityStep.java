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

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;

import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
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
			AWSSecurityTokenService sts = AWSClientFactory.create(AWSSecurityTokenServiceClientBuilder.standard(), this.getContext());
			GetCallerIdentityResult identity = sts.getCallerIdentity(new GetCallerIdentityRequest());

			this.getContext().get(TaskListener.class).getLogger().format("Current AWS identity: %s - %s - %s %n", identity.getAccount(), identity.getUserId(), identity.getArn());

			Map<String, String> info = new HashMap<>();
			info.put("account", identity.getAccount());
			info.put("user", identity.getUserId());
			info.put("arn", identity.getArn());
			return info;
		}

		private static final long serialVersionUID = 1L;

	}

}
