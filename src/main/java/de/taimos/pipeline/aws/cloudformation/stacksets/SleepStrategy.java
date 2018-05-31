package de.taimos.pipeline.aws.cloudformation.stacksets;

public interface SleepStrategy {

	long calculateSleepDuration (int attempt);

	SleepStrategy EXPONENTIAL_BACKOFF_STRATEGY = attempt -> (long) Math.max(Math.pow(2, attempt) * 100L, 60000);
}
