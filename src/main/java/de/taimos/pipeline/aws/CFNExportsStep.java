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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.Export;
import com.amazonaws.services.cloudformation.model.ListExportsRequest;
import com.amazonaws.services.cloudformation.model.ListExportsResult;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class CFNExportsStep extends AbstractStepImpl {
	
	@DataBoundConstructor
	public CFNExportsStep() {
		//
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "cfnExports";
		}
		
		@Override
		public String getDisplayName() {
			return "Describe CloudFormation global exports";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient CFNExportsStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			this.listener.getLogger().format("Getting global exports of CloudFormation %n");
			
			new Thread("cfnExports") {
				@Override
				public void run() {
					AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, Execution.this.envVars);
					ListExportsResult exports = client.listExports(new ListExportsRequest());
					
					Map<String, String> map = new HashMap<>();
					for (Export export : exports.getExports()) {
						map.put(export.getName(), export.getValue());
					}
					try {
						Execution.this.getContext().onSuccess(map);
					} catch (AmazonCloudFormationException e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}
		
		@Override
		public void stop(@Nonnull Throwable cause) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
