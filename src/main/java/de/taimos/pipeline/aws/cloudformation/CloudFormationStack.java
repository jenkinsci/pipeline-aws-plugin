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

package de.taimos.pipeline.aws.cloudformation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.Output;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;

import hudson.model.TaskListener;

public class CloudFormationStack {
	
	private final AmazonCloudFormationClient client;
	private final String stack;
	private final TaskListener listener;
	
	public CloudFormationStack(AmazonCloudFormationClient client, String stack, TaskListener listener) {
		this.client = client;
		this.stack = stack;
		this.listener = listener;
	}
	
	public boolean exists() {
		try {
			DescribeStacksResult result = this.client.describeStacks(new DescribeStacksRequest().withStackName(this.stack));
			return !result.getStacks().isEmpty();
		} catch (AmazonCloudFormationException e) {
			return false;
		}
	}
	
	public Map<String, String> describeOutputs() {
		DescribeStacksResult result = this.client.describeStacks(new DescribeStacksRequest().withStackName(this.stack));
		Stack cfnStack = result.getStacks().get(0);
		Map<String, String> map = new HashMap<>();
		for (Output output : cfnStack.getOutputs()) {
			map.put(output.getOutputKey(), output.getOutputValue());
		}
		return map;
	}
	
	public void create(String templateBody, Collection<Parameter> params, Collection<Tag> tags, Integer timeoutInMinutes) throws ExecutionException {
		CreateStackRequest req = new CreateStackRequest();
		req.withStackName(this.stack).withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM);
		req.withTemplateBody(templateBody).withParameters(params).withTags(tags).withTimeoutInMinutes(timeoutInMinutes);
		this.client.createStack(req);
		
		new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackCreateComplete());
	}
	
	public void update(String templateBody, Collection<Parameter> params, Collection<Tag> tags) throws ExecutionException {
		try {
			UpdateStackRequest req = new UpdateStackRequest();
			req.withStackName(this.stack).withCapabilities(Capability.CAPABILITY_IAM, Capability.CAPABILITY_NAMED_IAM);
			req.withTemplateBody(templateBody).withParameters(params).withTags(tags);
			this.client.updateStack(req);
			
			new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackUpdateComplete());
		} catch (AmazonCloudFormationException e) {
			if (e.getMessage().contains("No updates are to be performed")) {
				return;
			}
			throw e;
		}
	}
	
	public void delete() throws ExecutionException {
		this.client.deleteStack(new DeleteStackRequest().withStackName(this.stack));
		new EventPrinter(this.client, this.listener).waitAndPrintStackEvents(this.stack, this.client.waiters().stackDeleteComplete());
	}
}
