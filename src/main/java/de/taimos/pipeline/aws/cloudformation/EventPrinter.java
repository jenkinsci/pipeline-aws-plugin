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

import hudson.model.TaskListener;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsResponse;
import software.amazon.awssdk.services.cloudformation.model.StackEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Supplier;

class EventPrinter {

	private final CloudFormationClient client;
	private final TaskListener listener;

	EventPrinter(CloudFormationClient client, TaskListener listener) {
		this.client = client;
		this.listener = listener;
	}

	/**
	 * Runs a waiter while printing the stack's events as they arrive.
	 *
	 * v1 started the waiter with runAsync and polled events on the calling thread. v2's waiters are
	 * synchronous unless an asynchronous client is used, which would pull in a second HTTP client
	 * for no other reason, so the wait is handed to a background thread instead and the event loop
	 * below stays where it was. The waiter itself is supplied by the caller, mirroring how v1 was
	 * handed a Waiter for the specific operation.
	 */
	void waitAndPrintStackEvents(String stack, Supplier<?> waitOperation, PollConfiguration pollConfiguration) throws ExecutionException {
		this.listener.getLogger().println("Setting up a polling strategy to poll every " + pollConfiguration.getPollInterval() + " for a maximum of " + pollConfiguration.getTimeout());
		Future<?> waitResult = CompletableFuture.supplyAsync(waitOperation, waiterExecutor(stack));
		this.waitAndPrintEvents(stack, pollConfiguration, waitResult);
	}

	void waitAndPrintChangeSetEvents(String stack, String changeSet, Supplier<?> waitOperation, PollConfiguration pollConfiguration) throws ExecutionException {
		this.listener.getLogger().println("Setting up a polling strategy to poll every " + pollConfiguration.getPollInterval() + " for a maximum of " + pollConfiguration.getTimeout());
		Future<?> waitResult = CompletableFuture.supplyAsync(waitOperation, waiterExecutor(stack));
		this.waitAndPrintEvents(stack, pollConfiguration, waitResult);
	}

	/**
	 * A dedicated daemon thread per wait, rather than the common pool.
	 *
	 * The waiter blocks for as long as the configured timeout - ten minutes by default, and users
	 * set hours for large stacks - so running it on ForkJoinPool.commonPool() would park a worker
	 * that Jenkins core and other plugins share. v1's waiter.runAsync used the SDK's own executor
	 * and never touched the common pool either.
	 */
	private static Executor waiterExecutor(String stack) {
		return runnable -> {
			Thread thread = new Thread(runnable, "cfn-waiter-" + stack);
			thread.setDaemon(true);
			thread.start();
		};
	}

	private void waitAndPrintEvents(String stack, PollConfiguration pollConfiguration, Future<?> waitResult) throws ExecutionException {
		Instant startDate = Instant.now();
		String lastEventId = null;
		this.printLine();
		this.printStackName(stack);
		this.printLine();

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

		boolean run = true;
		if (pollConfiguration.getPollInterval().toMillis() > 0) {
			while (run && !waitResult.isDone()) {
				try {
					DescribeStackEventsResponse result = this.client.describeStackEvents(DescribeStackEventsRequest.builder().stackName(stack).build());
					List<StackEvent> stackEvents = new ArrayList<>();
					for (StackEvent event : result.stackEvents()) {
						if (event.eventId().equals(lastEventId) || event.timestamp().isBefore(startDate)) {
							break;
						}
						stackEvents.add(event);
					}
					if (!stackEvents.isEmpty()) {
						Collections.reverse(stackEvents);
						for (StackEvent event : stackEvents) {
							this.printEvent(formatter, event);
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

	private void printEvent(DateTimeFormatter formatter, StackEvent event) {
		String time = this.padRight(formatter.format(event.timestamp()), 25);
		String logicalResourceId = this.padRight(event.logicalResourceId(), 20);
		String resourceStatus = this.padRight(event.resourceStatusAsString(), 36);
		String resourceStatusReason = this.padRight(event.resourceStatusReason(), 140);
		this.listener.getLogger().format("| %s | %s | %s | %s |%n", time, logicalResourceId, resourceStatus, resourceStatusReason);
	}

	private void printLine() {
		this.listener.getLogger().println("-".repeat(231));
	}

	private void printStackName(String stackName) {
		this.listener.getLogger().println("| " + this.padRight("Stack: " + stackName, 227) + " |");
	}

	private String padRight(String s, int len) {
		return String.format("%1$-" + len + "s", (s != null ? s : "")).substring(0, len);
	}

}
