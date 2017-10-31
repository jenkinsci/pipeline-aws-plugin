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

import javax.inject.Inject;
import javax.xml.bind.DatatypeConverter;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.LogType;

import groovy.json.JsonBuilder;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import net.sf.json.JSONSerializer;

public class InvokeLambdaStep extends AbstractStepImpl {
	
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
			return this.toJsonString(this.payload);
		}
		return this.payloadAsString;
	}
	
	@DataBoundSetter
	public void setPayloadAsString(String payloadAsString) {
		this.payloadAsString = payloadAsString;
	}
	
	private String toJsonString(Object payloadObject) {
		return (new JsonBuilder(payloadObject)).toString();
	}
	
	public boolean isReturnValueAsString() {
		return this.returnValueAsString;
	}
	
	@DataBoundSetter
	public void setReturnValueAsString(boolean returnValueAsString) {
		this.returnValueAsString = returnValueAsString;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
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
	
	public static class Execution extends AbstractSynchronousStepExecution<Object> {
		
		private static final long serialVersionUID = 1L;
		
		@Inject
		private transient InvokeLambdaStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		protected Object run() throws Exception {
			AWSLambdaClient client = AWSClientFactory.create(AWSLambdaClient.class, this.envVars);
			
			String functionName = this.step.getFunctionName();
			
			this.listener.getLogger().format("Invoke Lambda function %s%n", functionName);
			
			InvokeRequest request = new InvokeRequest();
			request.withFunctionName(functionName);
			request.withPayload(this.step.getPayloadAsString());
			request.withLogType(LogType.Tail);
			
			InvokeResult result = client.invoke(request);
			
			this.listener.getLogger().append(this.getLogResult(result));
			String functionError = result.getFunctionError();
			if (functionError != null) {
				throw new RuntimeException("Invoke lambda failed! " + this.getPayloadAsString(result));
			}
			if (this.step.isReturnValueAsString()) {
				return this.getPayloadAsString(result);
			} else {
				return this.getPayloadAsObject(result);
			}
		}
		
		public Object getPayloadAsObject(InvokeResult result) {
			return JSONSerializer.toJSON(this.getPayloadAsString(result));
		}
		
		public String getPayloadAsString(InvokeResult result) {
			return new String(result.getPayload().array(), StandardCharsets.UTF_8);
		}
		
		private String getLogResult(InvokeResult result) {
			return new String(DatatypeConverter.parseBase64Binary(result.getLogResult()), StandardCharsets.UTF_8);
		}
		
	}
	
}
