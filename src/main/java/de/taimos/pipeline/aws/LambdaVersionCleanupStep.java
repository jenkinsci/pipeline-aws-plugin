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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.charset.Charset;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackResourcesResult;
import com.amazonaws.services.cloudformation.model.StackResource;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionRequest;
import com.amazonaws.services.lambda.model.ListVersionsByFunctionResult;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAlgorithm;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.base.Preconditions;

import java.util.Date;
import java.util.LinkedList;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

import java.time.ZonedDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;

public class LambdaVersionCleanupStep extends Step {

	private String functionName;
	private String stackName;
	private final ZonedDateTime versionCutoff;

	@DataBoundConstructor
	public LambdaVersionCleanupStep(int daysAgo) {
		this.versionCutoff = ZonedDateTime.now().minus(Period.ofDays(daysAgo));
	}

	@DataBoundSetter
	public void setFunctionName(String functionName) {
		this.functionName = functionName;
	}

	@DataBoundSetter
	public void setStackName(String stackName) {
		this.stackName = stackName;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new LambdaVersionCleanupStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "lambdaVersionCleanup";
		}

		@Override
		public String getDisplayName() {
			return "Cleanup old lambda versions";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<String> {

		protected static final long serialVersionUID = 1L;

		protected final transient LambdaVersionCleanupStep step;

		public Execution(LambdaVersionCleanupStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		private List<FunctionConfiguration> findAllVersions(AWSLambda client, String functionName) {
			List<FunctionConfiguration> list = new LinkedList<>();
			ListVersionsByFunctionRequest request = new ListVersionsByFunctionRequest()
				.withFunctionName(functionName);
			do {
				ListVersionsByFunctionResult result = client.listVersionsByFunction(request);
				list.addAll(result.getVersions());
				if (result.getNextMarker() != null) {
					request.setMarker(result.getNextMarker());
				}
			} while (request.getMarker() != null);

			return list;
		}

		private void deleteAllVersions(AWSLambda client, String functionName) throws Exception {
			TaskListener listener = Execution.this.getContext().get(TaskListener.class);
			listener.getLogger().format("Looking for old versions functionName=%s", functionName);
			List<FunctionConfiguration> allVersions = findAllVersions(client, functionName);
			listener.getLogger().format("Found old versions functionName=%s count=%d", functionName, allVersions.size());
			List<FunctionConfiguration> filteredVersions = allVersions.stream()
				.filter( (function) -> {
					ZonedDateTime parsedDateTime = ZonedDateTime.parse(function.getLastModified(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
					return parsedDateTime.isBefore(this.step.versionCutoff);
				})
				.collect(Collectors.toList());
			for (FunctionConfiguration functionConfiguration : filteredVersions) {
				listener.getLogger().format("Deleting old version functionName=%s version=%s", functionName, functionConfiguration.getVersion());
				client.deleteFunction(new DeleteFunctionRequest()
						.withFunctionName(functionName)
						.withQualifier(functionConfiguration.getVersion())
				);
			}
		}
		
		private void deleteAllStackFunctionVersions(AWSLambda client, String stackName) throws Exception  {
			TaskListener listener = Execution.this.getContext().get(TaskListener.class);
			AmazonCloudFormation cloudformation = AWSClientFactory.create(AmazonCloudFormationClientBuilder.standard(), this.getContext());
			DescribeStackResourcesResult result = cloudformation.describeStackResources(new DescribeStackResourcesRequest()
					.withStackName(stackName)
			);
			listener.getLogger().format("result: " + result);
			for (StackResource stackResource : result.getStackResources()) {
				if ("AWS::Lambda::Function".equals(stackResource.getResourceType())) {
					deleteAllVersions(client, stackResource.getPhysicalResourceId());
				}
			}
		}

		@Override
		public String run() throws Exception {

			AWSLambda client = AWSClientFactory.create(AWSLambdaClientBuilder.standard(), this.getContext());

			if (this.step.functionName != null) {
				deleteAllVersions(client, this.step.functionName);
			}

			if (this.step.stackName != null) {
				deleteAllStackFunctionVersions(client, this.step.stackName);
			}
			return null;
		}

	}

}
