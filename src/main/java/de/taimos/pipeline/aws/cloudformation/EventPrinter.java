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
 *	  http://www.apache.org/licenses/LICENSE-2.0
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.apache.commons.lang.StringUtils;

import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cloudformation.model.StackEvent;

import de.taimos.pipeline.aws.cloudformation.utils.TimeOutRetryStrategy;
import hudson.model.TaskListener;

import software.amazon.awssdk.services.cloudformation.waiters.CloudFormationAsyncWaiter;

class EventPrinter {

	private static final int DEFAULT_TIMEOUT_IN_MINUTES = 60;

	private final CloudFormationClient client;
	private final TaskListener listener;

	EventPrinter(CloudFormationClient client, TaskListener listener) {
		this.client = client;
		this.listener = listener;
	}

	void waitAndPrintChangeSetEvents(String stack, String changeSet, CloudFormationAsyncWaiter waiter,
									 PollConfiguration pollConfiguration) throws ExecutionException {

		final CompletableFuture<DescribeChangeSetResponse> waitResult =

		waiter.waitUntilChangeSetCreateComplete(DescribeChangeSetRequest.builder().stackName(stack).changeSetName(changeSet).build())
			.handle((res, ex) -> {
			ResponseOrException<DescribeChangeSetResponse> matched = res.matched();
			if (matched.response().isPresent()) {
				return matched.response().orElseThrow();
			} else {
				throw toException(ex);
			}
		});

		this.waitAndPrintEvents(stack, pollConfiguration, waitResult);
	}

	RuntimeException toException(Throwable throwable) {
		return throwable instanceof RuntimeException || throwable == null ? (RuntimeException) throwable : new RuntimeException(throwable);
	}

	<T> void waitAndPrintStackEvents(String stack, Function<DescribeStacksRequest, CompletableFuture<WaiterResponse<DescribeStacksResponse>>> waiter, PollConfiguration pollConfiguration) throws ExecutionException {
		CompletableFuture<?> res = waiter.apply(DescribeStacksRequest.builder().stackName(stack).build());
		this.waitAndPrintEvents(stack, pollConfiguration, res);
	}

	private void waitAndPrintEvents(String stack, PollConfiguration pollConfiguration, CompletableFuture<?> waitResult) throws ExecutionException {
		Instant startDate = Instant.now();
		String lastEventId = null;
		this.printLine();
		this.printStackName(stack);
		this.printLine();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

		boolean run = true;
		if (pollConfiguration.getPollInterval().toMillis() > 0) {
			while (run && !waitResult.isDone()) {
				try {
					DescribeStackEventsResponse result = this.client.describeStackEvents(DescribeStackEventsRequest.builder().stackName(stack).build());
					List<StackEvent> stackEvents = new ArrayList<>();
					for (StackEvent event : result.stackEvents()) {
						if (event.eventId().equals(lastEventId) || event.timestamp().compareTo(startDate) < 0) {
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
						lastEventId = stackEvents.get(stackEvents.size() - 1).eventId();
					}
				} catch (CloudFormationException e) {
					// suppress and continue
				}
				try {
					Thread.sleep(pollConfiguration.getPollInterval().toMillis());
				} catch (InterruptedException e) {
					// suppress and continue
					this.listener.getLogger().print("Task interrupted. Stopping event printer.");
					run = false;
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
		String time = this.padRight(sdf.format(event.timestamp()), 25);
		String logicalResourceId = this.padRight(event.logicalResourceId(), 20);
		String resourceStatus = this.padRight(event.resourceStatus().name(), 36);
		String resourceStatusReason = this.padRight(event.resourceStatusReason(), 140);
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
