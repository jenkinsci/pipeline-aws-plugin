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

import javax.xml.bind.DatatypeConverter;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.LogType;

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
			AWSLambda client = AWSClientFactory.create(AWSLambdaClientBuilder.standard(), this.getContext());

			String functionName = this.step.getFunctionName();

			listener.getLogger().format("Invoke Lambda function %s%n", functionName);

			InvokeRequest request = new InvokeRequest();
			request.withFunctionName(functionName);
			request.withPayload(this.step.getPayloadAsString());
			request.withLogType(LogType.Tail);

			InvokeResult result = client.invoke(request);

			listener.getLogger().append(this.getLogResult(result));
			String functionError = result.getFunctionError();
			if (functionError != null) {
				throw new RuntimeException("Invoke lambda failed! " + this.getPayloadAsString(result));
			}
			if (this.step.isReturnValueAsString()) {
				return this.getPayloadAsString(result);
			} else {
				return JsonUtils.fromString(this.getPayloadAsString(result));
			}
		}

		private String getPayloadAsString(InvokeResult result) {
			return new String(result.getPayload().array(), StandardCharsets.UTF_8);
		}

		private String getLogResult(InvokeResult result) {
			return new String(DatatypeConverter.parseBase64Binary(result.getLogResult()), StandardCharsets.UTF_8);
		}

	}

}
