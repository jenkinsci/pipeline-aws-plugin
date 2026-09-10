/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2017 Taimos GmbH
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

import java.nio.charset.StandardCharsets;
import java.util.Set;

import java.util.Base64;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LogType;

import de.taimos.pipeline.aws.utils.JsonUtils;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

public class InvokeLambdaStep extends Step {

	private Object payload;
	private String payloadAsString;
	private boolean returnValueAsString = false;
	private final String functionName;

	@DataBoundConstructor
	public InvokeLambdaStep(String functionName) {
		this.functionName = functionName;
	}

	public String getFunctionName() {
		return this.functionName;
	}

	public Object getPayload() {
		return this.payload;
	}

	@DataBoundSetter
	public void setPayload(Object payload) {
		this.payload = payload;
	}

	public String getPayloadAsString() {
		if (this.payload != null) {
			return JsonUtils.toString(this.payload);
		}
		return this.payloadAsString;
	}

	@DataBoundSetter
	public void setPayloadAsString(String payloadAsString) {
		this.payloadAsString = payloadAsString;
	}

	public boolean isReturnValueAsString() {
		return this.returnValueAsString;
	}

	@DataBoundSetter
	public void setReturnValueAsString(boolean returnValueAsString) {
		this.returnValueAsString = returnValueAsString;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new InvokeLambdaStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "invokeLambda";
		}

		@Override
		public String getDisplayName() {
			return "Invoke a given Lambda function";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Object> {

		private static final long serialVersionUID = 1L;

		private final transient InvokeLambdaStep step;

		public Execution(InvokeLambdaStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Object run() throws Exception {
			TaskListener listener = this.getContext().get(TaskListener.class);
			LambdaClient client = AWSClientFactory.create(LambdaClient.builder(), this.getContext());

			String functionName = this.step.getFunctionName();

			listener.getLogger().format("Invoke Lambda function %s%n", functionName);

			InvokeRequest.Builder request = InvokeRequest.builder()
					.functionName(functionName)
					.logType(LogType.TAIL);
			// Both payload parameters are optional and Invoke accepts a request without one, but
			// SdkBytes.fromString throws on null where v1's withPayload(String) accepted it.
			String payload = this.step.getPayloadAsString();
			if (payload != null) {
				request.payload(SdkBytes.fromString(payload, StandardCharsets.UTF_8));
			}

			InvokeResponse result = client.invoke(request.build());

			listener.getLogger().append(this.getLogResult(result));
			String functionError = result.functionError();
			if (functionError != null) {
				throw new RuntimeException("Invoke lambda failed! " + this.getPayloadAsString(result));
			}
			if (this.step.isReturnValueAsString()) {
				return this.getPayloadAsString(result);
			} else {
				return JsonUtils.fromString(this.getPayloadAsString(result));
			}
		}

		private String getPayloadAsString(InvokeResponse result) {
			return result.payload().asString(StandardCharsets.UTF_8);
		}

		private String getLogResult(InvokeResponse result) {
			return new String(Base64.getDecoder().decode(result.logResult()), StandardCharsets.UTF_8);
		}

	}

}
