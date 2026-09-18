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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.cloudformation.model.ListStackResourcesRequest;
import software.amazon.awssdk.services.cloudformation.model.ListStackResourcesResponse;
import software.amazon.awssdk.services.cloudformation.model.StackResourceSummary;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.ListAliasesRequest;

import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

import java.time.ZonedDateTime;
import java.time.Period;

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

		private List<FunctionConfiguration> findAllVersions(LambdaClient client, String functionName) {
			// v1 walked the marker by hand; the paginator issues the same sequence of calls
			return client.listVersionsByFunctionPaginator(ListVersionsByFunctionRequest.builder()
							.functionName(functionName)
							.build())
					.stream()
					.flatMap(page -> page.versions().stream())
					.collect(Collectors.toList());
		}

		private void deleteAllVersions(LambdaClient client, String functionName) throws Exception {
			TaskListener listener = Execution.this.getContext().get(TaskListener.class);
			listener.getLogger().format("Looking for old versions functionName=%s%n", functionName);
			List<String> aliasedVersions = client.listAliases(ListAliasesRequest.builder()
					.functionName(functionName)
					.build()).aliases().stream()
					.map(alias -> alias.functionVersion())
					.collect(Collectors.toList());
			listener.getLogger().format("Found alises functionName=%s alias=%s%n", functionName, aliasedVersions);
			List<FunctionConfiguration> allVersions = findAllVersions(client, functionName);
			listener.getLogger().format("Found old versions functionName=%s count=%d%n", functionName, allVersions.size());
			List<FunctionConfiguration> filteredVersions = allVersions.stream()
				.filter( (function) -> {
					ZonedDateTime parsedDateTime = DateTimeUtils.parse(function.lastModified());
					return parsedDateTime.isBefore(this.step.versionCutoff);
				})
				.filter( (function) -> !"$LATEST".equals(function.version()))
				.filter( (function) -> !aliasedVersions.contains(function.version()))
				.collect(Collectors.toList());
			for (FunctionConfiguration functionConfiguration : filteredVersions) {
				listener.getLogger().format("Deleting old version functionName=%s version=%s lastModified=%s%n", functionName, functionConfiguration.version(), functionConfiguration.lastModified());
				client.deleteFunction(DeleteFunctionRequest.builder()
						.functionName(functionName)
						.qualifier(functionConfiguration.version())
						.build()
				);
			}
		}

		private List<StackResourceSummary> findAllResourcesForStack(String stackName) {
			CloudFormationClient cloudformation = AWSClientFactory.create(CloudFormationClient.builder(), this.getContext());
			List<StackResourceSummary> stackResources = new ArrayList<>();
			for (ListStackResourcesResponse page : cloudformation.listStackResourcesPaginator(
					ListStackResourcesRequest.builder().stackName(stackName).build())) {
				stackResources.addAll(page.stackResourceSummaries());
			}
			return stackResources;
		}

		private void deleteAllStackFunctionVersions(LambdaClient client, String stackName) throws Exception  {
			TaskListener listener = Execution.this.getContext().get(TaskListener.class);
			listener.getLogger().format("Deleting old versions from stackName=%s%n", stackName);
			List<StackResourceSummary> stackResources = findAllResourcesForStack(stackName);
			listener.getLogger().format("Found %d resources in stackName=%s%n", stackResources.size(), stackName);
			List<StackResourceSummary> lambdaFunctions = stackResources.stream()
					.filter(resource -> "AWS::Lambda::Function".equals(resource.resourceType()))
					.collect(Collectors.toList());
			listener.getLogger().format("Found %d lambda resources in stackName=%s%n", lambdaFunctions.size(), stackName);
			for (StackResourceSummary stackResource : lambdaFunctions) {
				deleteAllVersions(client, stackResource.physicalResourceId());
			}
		}

		@Override
		public String run() throws Exception {

			LambdaClient client = AWSClientFactory.create(LambdaClient.builder(), this.getContext());

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
