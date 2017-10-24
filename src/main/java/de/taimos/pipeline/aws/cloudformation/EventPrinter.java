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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.amazonaws.services.cloudformation.model.*;
import org.apache.commons.lang.StringUtils;
import org.apache.http.concurrent.BasicFuture;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.waiters.Waiter;
import com.amazonaws.waiters.WaiterHandler;
import com.amazonaws.waiters.WaiterParameters;

import hudson.model.TaskListener;

public class EventPrinter {
	
	private final AmazonCloudFormationClient client;
	private final TaskListener listener;
	
	public EventPrinter(AmazonCloudFormationClient client, TaskListener listener) {
		this.client = client;
		this.listener = listener;
	}

	public void waitAndPrintChangeSetEvents(String stack, String changeSet, Waiter<DescribeChangeSetRequest> waiter, long pollIntervalMillis) throws ExecutionException {

		final BasicFuture<AmazonWebServiceRequest> waitResult = new BasicFuture<>(null);

		waiter.runAsync(new WaiterParameters<>(new DescribeChangeSetRequest().withStackName(stack).withChangeSetName(changeSet)), new WaiterHandler() {
			@Override
			public void onWaitSuccess(AmazonWebServiceRequest request) {
				waitResult.completed(request);
			}

			@Override
			public void onWaitFailure(Exception e) {
				waitResult.failed(e);
			}
		});

		waitAndPrintEvents(stack, pollIntervalMillis, waitResult);
	}

	public void waitAndPrintStackEvents(String stack, Waiter<DescribeStacksRequest> waiter, long pollIntervalMillis) throws ExecutionException {

		final BasicFuture<AmazonWebServiceRequest> waitResult = new BasicFuture<>(null);
		
		waiter.runAsync(new WaiterParameters<>(new DescribeStacksRequest().withStackName(stack)), new WaiterHandler() {
			@Override
			public void onWaitSuccess(AmazonWebServiceRequest request) {
				waitResult.completed(request);
			}
			
			@Override
			public void onWaitFailure(Exception e) {
				waitResult.failed(e);
			}
		});

		waitAndPrintEvents(stack, pollIntervalMillis, waitResult);
	}

	private void waitAndPrintEvents(String stack, long pollIntervalMillis, BasicFuture<AmazonWebServiceRequest> waitResult) throws ExecutionException {
		Date startDate = new Date();

		String lastEventId = null;
		this.printLine();
		this.printStackName(stack);
		this.printLine();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

		if (pollIntervalMillis > 0) {
			while (!waitResult.isDone()) {
				try {
					DescribeStackEventsResult result = this.client.describeStackEvents(new DescribeStackEventsRequest().withStackName(stack));
					List<StackEvent> stackEvents = new ArrayList<>();
					for (StackEvent event : result.getStackEvents()) {
						if (event.getEventId().equals(lastEventId) || event.getTimestamp().before(startDate)) {
							break;
						}
						stackEvents.add(event);
					}
					if (!stackEvents.isEmpty()) {
						Collections.reverse(stackEvents);
						for (StackEvent event : stackEvents) {
							this.printEvent(sdf, event);
							this.printLine();
						}
						lastEventId = stackEvents.get(stackEvents.size() - 1).getEventId();
					}
				} catch (AmazonCloudFormationException e) {
					// suppress and continue
				}
				try {
					Thread.sleep(pollIntervalMillis);
				} catch (InterruptedException e) {
					// suppress and continue
				}
			}
		}

		try {
			waitResult.get();
		} catch (InterruptedException e) {
			this.listener.getLogger().format("Failed to wait for CFN action to complete: %s", e.getMessage());
		}
	}

	private void printEvent(SimpleDateFormat sdf, StackEvent event) {
		String time = this.padRight(sdf.format(event.getTimestamp()), 25);
		String logicalResourceId = this.padRight(event.getLogicalResourceId(), 20);
		String resourceStatus = this.padRight(event.getResourceStatus(), 36);
		String resourceStatusReason = this.padRight(event.getResourceStatusReason(), 140);
		this.listener.getLogger().format("| %s | %s | %s | %s |%n", time, logicalResourceId, resourceStatus, resourceStatusReason);
	}
	
	private void printLine() {
		this.listener.getLogger().println(StringUtils.repeat("-", 231));
	}
	
	private void printStackName(String stackName) {
		this.listener.getLogger().println("| " + this.padRight("Stack: " + stackName, 227) + " |");
	}
	
	private String padRight(String s, int len) {
		return String.format("%1$-" + len + "s", (s != null ? s : "")).substring(0, len);
	}
	
}
