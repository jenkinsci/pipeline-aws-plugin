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

}
