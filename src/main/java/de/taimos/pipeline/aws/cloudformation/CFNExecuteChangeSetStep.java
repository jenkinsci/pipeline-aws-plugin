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

package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.ChangeSetType;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.cloudformation.parser.JSONParameterFileParser;
import de.taimos.pipeline.aws.cloudformation.parser.ParameterFileParser;
import de.taimos.pipeline.aws.cloudformation.parser.YAMLParameterFileParser;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;

public class CFNExecuteChangeSetStep extends AbstractStepImpl {

	private final String changeSet;
	private final String stack;
	private Long pollInterval = 1000L;

	@DataBoundConstructor
	public CFNExecuteChangeSetStep(String changeSet, String stack) {
		this.changeSet = changeSet;
		this.stack = stack;
	}

	public String getChangeSet() {
		return this.changeSet;
	}

	public String getStack() {
		return this.stack;
	}

	public Long getPollInterval() {
		return this.pollInterval;
	}
	
	@DataBoundSetter
	public void setPollInterval(Long pollInterval) {
		this.pollInterval = pollInterval;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "cfnExecuteChangeSet";
		}
		
		@Override
		public String getDisplayName() {
			return "Execute CloudFormation change set";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient CFNExecuteChangeSetStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String changeSet = this.step.getChangeSet();
			final String stack = this.step.getStack();

			Preconditions.checkArgument(changeSet != null && !changeSet.isEmpty(), "Change Set must not be null or empty");

			Preconditions.checkArgument(stack != null && !stack.isEmpty(), "Stack must not be null or empty");

			this.listener.getLogger().format("Executing CloudFormation change set %s %n", changeSet);
			
			new Thread("cfnExecuteChangeSet-" + changeSet) {
				@Override
				public void run() {
					try {
						AmazonCloudFormationClient client = AWSClientFactory.create(AmazonCloudFormationClient.class, Execution.this.envVars);
						CloudFormationStack cfnStack = new CloudFormationStack(client, stack, Execution.this.listener);
						cfnStack.executeChangeSet(changeSet, Execution.this.step.getPollInterval());
						Execution.this.listener.getLogger().println("Execute change set complete");
						Execution.this.getContext().onSuccess(cfnStack.describeOutputs());
					} catch (Exception e) {
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
