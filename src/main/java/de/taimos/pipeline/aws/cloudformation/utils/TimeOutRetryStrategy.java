package de.taimos.pipeline.aws.cloudformation.utils;

import java.time.Duration;
import java.time.OffsetDateTime;

public class TimeOutRetryStrategy  {

	private final OffsetDateTime start;
	private final Duration maxTime;

	public TimeOutRetryStrategy(Duration maxTime) {
		this.start = OffsetDateTime.now();
		this.maxTime = maxTime;
	}

	public boolean shouldRetry() {
		Duration difference = Duration.between(this.start, OffsetDateTime.now());
		return difference.compareTo(this.maxTime) < 0;
	}
}
