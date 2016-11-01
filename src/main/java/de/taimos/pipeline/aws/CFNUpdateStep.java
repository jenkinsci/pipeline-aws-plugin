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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.waiters.WaiterParameters;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class CFNUpdateStep extends AbstractStepImpl {
	
	private final String stack;
	private final String file;
	private String[] params;
	private String[] keepParams;
	
	@DataBoundConstructor
	public CFNUpdateStep(String stack, String file) {
		this.stack = stack;
		this.file = file;
	}
	
	public String getStack() {
		return this.stack;
	}
	
	public String getFile() {
		return this.file;
	}
	
	public String[] getParams() {
		return this.params != null ? this.params.clone() : null;
	}
	
	@DataBoundSetter
	public void setParams(String[] params) {
		this.params = params.clone();
	}
	
	public String[] getKeepParams() {
		return this.keepParams != null ? this.keepParams.clone() : null;
	}
	
	@DataBoundSetter
	public void setKeepParams(String[] keepParams) {
		this.keepParams = keepParams.clone();
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "cfnUpdate";
		}
		
		@Override
		public String getDisplayName() {
			return "Create or Update CloudFormation stack";
		}
	}
	
	public static class Execution extends AbstractSynchronousStepExecution<Map<String, String>> {
		
		@Inject
		private transient CFNUpdateStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		protected Map<String, String> run() throws Exception {
			AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, this.envVars);
			
			String stack = this.step.getStack();
			String file = this.step.getFile();
			Collection<Parameter> params = this.parseParams(this.step.getParams());
			Collection<Parameter> keepParams = this.parseKeepParams(this.step.getKeepParams());
			
			this.listener.getLogger().format("Updating/Creating CloudFormation stack %s %n", stack);
			
			if (this.stackExists(client, stack)) {
				ArrayList<Parameter> parameters = new ArrayList<>(params);
				parameters.addAll(keepParams);
				this.updateStack(client, stack, file, parameters);
			} else {
				this.createStack(client, stack, file, params);
			}
			this.listener.getLogger().println("Stack update complete");
			return this.describeOutputs(client, stack);
		}
		
		private Map<String, String> describeOutputs(AmazonCloudFormationClient client, String stack) {
			DescribeStacksResult result = client.describeStacks(new DescribeStacksRequest().withStackName(stack));
			Stack cfnStack = result.getStacks().get(0);
			Map<String, String> map = new HashMap<>();
			for (Output output : cfnStack.getOutputs()) {
				map.put(output.getOutputKey(), output.getOutputValue());
			}
			return map;
		}
		
		private void createStack(AmazonCloudFormationClient client, String stack, String file, Collection<Parameter> params) {
			CreateStackRequest req = new CreateStackRequest();
			req.withStackName(stack).withCapabilities(Capability.CAPABILITY_IAM);
			req.withTemplateBody(this.readTemplate(file)).withParameters(params);
			client.createStack(req);
			
			client.waiters().stackCreateComplete().run(new WaiterParameters<>(new DescribeStacksRequest().withStackName(stack)));
		}
		
		private void updateStack(AmazonCloudFormationClient client, String stack, String file, Collection<Parameter> params) {
			try {
				UpdateStackRequest req = new UpdateStackRequest();
				req.withStackName(stack).withCapabilities(Capability.CAPABILITY_IAM);
				req.withTemplateBody(this.readTemplate(file)).withParameters(params);
				client.updateStack(req);
				
				client.waiters().stackUpdateComplete().run(new WaiterParameters<>(new DescribeStacksRequest().withStackName(stack)));
			} catch (AmazonCloudFormationException e) {
				if (e.getMessage().contains("No updates are to be performed")) {
					return;
				}
				throw e;
			}
		}
		
		private String readTemplate(String file) {
			FilePath child = this.workspace.child(file);
			try {
				return child.readToString();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		private boolean stackExists(AmazonCloudFormationClient client, String stack) {
			try {
				DescribeStacksResult result = client.describeStacks(new DescribeStacksRequest().withStackName(stack));
				return !result.getStacks().isEmpty();
			} catch (AmazonCloudFormationException e) {
				return false;
			}
		}
		
		private Collection<Parameter> parseParams(String[] params) {
			Collection<Parameter> parameters = new ArrayList<>();
			if (params == null) {
				return parameters;
			}
			for (String param : params) {
				int i = param.indexOf("=");
				if (i < 0) {
					throw new RuntimeException("Missing = in param " + param);
				}
				String key = param.substring(0, i);
				String value = param.substring(i + 1);
				parameters.add(new Parameter().withParameterKey(key).withParameterValue(value));
			}
			return parameters;
		}
		
		private Collection<Parameter> parseKeepParams(String[] params) {
			Collection<Parameter> parameters = new ArrayList<>();
			if (params == null) {
				return parameters;
			}
			for (String param : params) {
				parameters.add(new Parameter().withParameterKey(param).withUsePreviousValue(true));
			}
			return parameters;
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
