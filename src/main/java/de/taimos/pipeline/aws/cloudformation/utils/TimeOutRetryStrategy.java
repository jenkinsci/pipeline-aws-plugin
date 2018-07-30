package de.taimos.pipeline.aws.cloudformation.utils;

import java.time.Duration;
import java.time.OffsetDateTime;

import com.amazonaws.waiters.PollingStrategy;
import com.amazonaws.waiters.PollingStrategyContext;

public class TimeOutRetryStrategy implements PollingStrategy.RetryStrategy {

	private final OffsetDateTime start;
	private final Duration maxTime;

	public TimeOutRetryStrategy(Duration maxTime) {
		this.start = OffsetDateTime.now();
		this.maxTime = maxTime;
	}

	@Override
	public boolean shouldRetry(PollingStrategyContext pollingStrategyContext) {
		Duration difference = Duration.between(this.start, OffsetDateTime.now());
		return difference.compareTo(this.maxTime) < 0;
	}
}
