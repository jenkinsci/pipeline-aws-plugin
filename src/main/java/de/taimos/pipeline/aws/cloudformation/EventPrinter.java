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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.AmazonCloudFormationException;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsRequest;
import com.amazonaws.services.cloudformation.model.DescribeStackEventsResult;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.StackEvent;
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
	
	public void waitAndPrintStackEvents(String stack, Waiter<DescribeStacksRequest> waiter) {
		Date startDate = new Date();
		
		final AtomicBoolean done = new AtomicBoolean(false);
		
		waiter.runAsync(new WaiterParameters<>(new DescribeStacksRequest().withStackName(stack)), new WaiterHandler() {
			@Override
			public void onWaitSuccess(AmazonWebServiceRequest request) {
				done.set(true);
			}
			
			@Override
			public void onWaitFailure(Exception e) {
				done.set(true);
			}
		});
		
		String lastEventId = null;
		this.printLine();
		this.printStackName(stack);
		this.printLine();
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		
		while (!done.get()) {
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
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
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
