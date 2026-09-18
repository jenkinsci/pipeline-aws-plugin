package de.taimos.pipeline.aws.cloudformation;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import java.time.Duration;

@Value
@Builder(toBuilder = true)
@SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE") // Lombok @NonNull check
public class PollConfiguration {

	public static final PollConfiguration DEFAULT = builder()
			.timeout(Duration.ofMinutes(10))
			.pollInterval(Duration.ofSeconds(1))
			.build();

	@NonNull
	Duration timeout, pollInterval;

	/**
	 * pollInterval: 0 is documented as disabling event printing, which EventPrinter honours by
	 * skipping its loop. It says nothing about how often the underlying wait should call AWS, and a
	 * zero delay there means a tight loop against the API for the whole wait - so a non-positive
	 * interval is substituted with one second.
	 *
	 * Every value that is at least a millisecond is honoured exactly: pollInterval is in
	 * milliseconds, so 250 is a legal thing to ask for.
	 *
	 * The test is on the value in milliseconds, not on the Duration's sign, because both consumers
	 * work in milliseconds - Thread.sleep for the stack-set loops, a fixed backoff for the waiters -
	 * so a positive but sub-millisecond interval would round to zero there and become the very tight
	 * loop this exists to prevent. Pipelines cannot express one (TemplateStepBase builds the Duration
	 * with ofMillis), but the guard should not depend on that.
	 *
	 * Both waiting implementations go through this, so the CloudFormation waiters and the stack-set
	 * poll loops cannot drift apart on it.
	 */
	public static final Duration DISABLED_POLL_INTERVAL_SUBSTITUTE = Duration.ofSeconds(1);

	public static Duration effectivePollInterval(Duration pollInterval) {
		if (pollInterval.toMillis() <= 0) {
			return DISABLED_POLL_INTERVAL_SUBSTITUTE;
		}
		return pollInterval;
	}

	public Duration getEffectivePollInterval() {
		return effectivePollInterval(this.getPollInterval());
	}

}
