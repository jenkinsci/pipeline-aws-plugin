/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2026 Taimos GmbH
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

import org.junit.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The substitution the CloudFormation waiters and the stack-set poll loops share. Both used to carry
 * their own copy of this rule; testing it here is what keeps "the stack sets get the same treatment
 * as the cfnUpdate waiters" true rather than aspirational.
 */
public class PollConfigurationTest {

	@Test
	public void aNonPositiveIntervalBecomesOneSecond() {
		assertThat(PollConfiguration.effectivePollInterval(Duration.ZERO)).isEqualTo(Duration.ofSeconds(1));
		assertThat(PollConfiguration.effectivePollInterval(Duration.ofMillis(-5))).isEqualTo(Duration.ofSeconds(1));
	}

	/**
	 * Both consumers work in milliseconds - Thread.sleep for the stack-set loops, a fixed backoff for
	 * the waiters - so an interval that rounds to zero there is treated as disabled rather than
	 * honoured as "no delay".
	 */
	@Test
	public void aSubMillisecondIntervalCountsAsDisabled() {
		assertThat(PollConfiguration.effectivePollInterval(Duration.ofNanos(1))).isEqualTo(Duration.ofSeconds(1));
		assertThat(PollConfiguration.effectivePollInterval(Duration.ofNanos(999_999))).isEqualTo(Duration.ofSeconds(1));
	}

	/**
	 * pollInterval is in milliseconds, so sub-second values are legal and must survive untouched.
	 */
	@Test
	public void positiveIntervalsAreHonouredExactly() {
		assertThat(PollConfiguration.effectivePollInterval(Duration.ofMillis(1))).isEqualTo(Duration.ofMillis(1));
		assertThat(PollConfiguration.effectivePollInterval(Duration.ofMillis(250))).isEqualTo(Duration.ofMillis(250));
		assertThat(PollConfiguration.effectivePollInterval(Duration.ofSeconds(30))).isEqualTo(Duration.ofSeconds(30));
	}

	@Test
	public void theInstanceFormReadsThePollInterval() {
		assertThat(PollConfiguration.builder()
				.timeout(Duration.ofMinutes(10))
				.pollInterval(Duration.ZERO)
				.build()
				.getEffectivePollInterval()).isEqualTo(Duration.ofSeconds(1));

		assertThat(PollConfiguration.DEFAULT.getEffectivePollInterval()).isEqualTo(Duration.ofSeconds(1));
	}
}
