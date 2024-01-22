package de.taimos.pipeline.aws.cloudformation.utils;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.waiters.PollingStrategyContext;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;

public class TimeOutRetryStrategyTests {

	@Test
	public void retriesLessThanMaxTime() throws InterruptedException {
		AmazonWebServiceRequest request = Mockito.mock(AmazonWebServiceRequest.class);

		TimeOutRetryStrategy retryStrategy = new TimeOutRetryStrategy(Duration.ofSeconds(1));

		Assertions.assertThat(retryStrategy.shouldRetry(pollingStrategyContext(request, 5))).isTrue();

		Thread.sleep(1100);
		Assertions.assertThat(retryStrategy.shouldRetry(pollingStrategyContext(request, 1))).isFalse();
	}

	/**
	 * the aws-sdk-java (currently as of 7/12/2018) does not have a publicly available constructor to create a PollingStrategyContext.
	 */
	private PollingStrategyContext pollingStrategyContext(AmazonWebServiceRequest request, int retriesAttempted) {
		try {
			Constructor<PollingStrategyContext> constructor = PollingStrategyContext.class.getDeclaredConstructor(AmazonWebServiceRequest.class, int.class);
			constructor.setAccessible(true);
			return constructor.newInstance(request, retriesAttempted);
		} catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
			throw new IllegalStateException("Could not create a PollingStrategyContext", e);
		}
	}
}
