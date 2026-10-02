package de.taimos.pipeline.aws.eb;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These steps poll in an unbounded while(true) loop, so a regression in how the status is read
 * would hang the build instead of failing it. The timeout turns that back into a test failure.
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class EBWaitOnEnvironmentHealthStepTest {

    @Captor
    private ArgumentCaptor<DescribeEnvironmentsRequest> describeCaptor;

    private static StepContext context;

	@BeforeAll
	static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

	@AfterEach
	void resetClient() {
        // the factory delegate is static and would otherwise stay installed for whichever test
        // class runs next in the same JVM, handing it a mocked ElasticBeanstalkClient
        EBTestingUtils.resetElasticBeanstalkClient();
    }

	@Test
	void stepDescriptorNameIsAsExpected() {
        EBWaitOnEnvironmentHealthStep.DescriptorImpl stepDescriptor = new EBWaitOnEnvironmentHealthStep.DescriptorImpl();
        assertEquals("ebWaitOnEnvironmentHealth", stepDescriptor.getFunctionName());
    }

	/**
	 * "Green" is a modelled EnvironmentHealth, so health().toString() would pass the test below
	 * too. Only a value outside the enum distinguishes healthAsString(), and there a regression
	 * hangs this step's polling loop rather than failing it.
	 */
	@Test
	void waitStopsOnAHealthOutsideTheEnum() throws Exception {
        EBWaitOnEnvironmentHealthStep step = new EBWaitOnEnvironmentHealthStep("my application", "my-environment");
        step.setHealth("SomeFutureHealth");
        step.setStabilityThreshold(0);
        EBWaitOnEnvironmentHealthStep.Execution execution = new EBWaitOnEnvironmentHealthStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .health("SomeFutureHealth")
                .build();
        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        when(client.describeEnvironments(any(DescribeEnvironmentsRequest.class))).thenReturn(result);

        execution.run();

        // returning at all is the point - an enum accessor here would loop forever - but the poll
        // count is bounded too, so an extra round trip does not slip through unnoticed. With a
        // zero threshold the step returns on the first poll if a millisecond has already elapsed
        // and on the second otherwise, so the bound is at most two rather than a fixed count.
        verify(client, atMost(2)).describeEnvironments(any(DescribeEnvironmentsRequest.class));
    }

	@Test
	void waitStopImmediatelyAfterFindingGreenHealthForNoThreshold() throws Exception {
        EBWaitOnEnvironmentHealthStep step = new EBWaitOnEnvironmentHealthStep("my application", "my-environment");
        step.setStabilityThreshold(0);
        EBWaitOnEnvironmentHealthStep.Execution execution = new EBWaitOnEnvironmentHealthStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .health("Green")
                .build();
        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        doReturn(result).when(client).describeEnvironments(any(DescribeEnvironmentsRequest.class));

        execution.run();

        verify(client, atLeastOnce()).describeEnvironments(describeCaptor.capture());
        assertEquals("my application", describeCaptor.getValue().applicationName());
        assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }
}
