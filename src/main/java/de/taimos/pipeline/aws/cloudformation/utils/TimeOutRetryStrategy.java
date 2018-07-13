package de.taimos.pipeline.aws.cloudformation.utils;

import com.amazonaws.waiters.PollingStrategy;
import com.amazonaws.waiters.PollingStrategyContext;

import java.time.Duration;
import java.time.OffsetDateTime;

public class TimeOutRetryStrategy implements PollingStrategy.RetryStrategy {

	private final OffsetDateTime start;
	private final Duration maxTime;

	public TimeOutRetryStrategy(Duration maxTime) {
		this.start = OffsetDateTime.now();
		this.maxTime = maxTime;
	}

	@Override
	public boolean shouldRetry(PollingStrategyContext pollingStrategyContext) {
		Duration difference = Duration.between(start, OffsetDateTime.now());
		return difference.compareTo(maxTime) < 0;
	}
}
