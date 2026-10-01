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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These steps poll in an unbounded while(true) loop, so a regression in how the status is read
 * would hang the build instead of failing it. The timeout turns that back into a test failure.
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class EBWaitOnEnvironmentStatusStepTest {

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
        EBWaitOnEnvironmentStatusStep.DescriptorImpl stepDescriptor = new EBWaitOnEnvironmentStatusStep.DescriptorImpl();
        assertEquals("ebWaitOnEnvironmentStatus", stepDescriptor.getFunctionName());
    }

	/**
	 * "Ready" is a modelled EnvironmentStatus, so status().toString() would satisfy the test above
	 * just as well as statusAsString(). The two only diverge for a value outside the enum - a
	 * status AWS adds later - where the enum accessor yields UNKNOWN_TO_SDK_VERSION, rendering as
	 * the literal "null", and this step's unbounded polling loop would never terminate: a hung
	 * build rather than a failing one.
	 */
	@Test
	void waitStopsOnAStatusOutsideTheEnum() throws Exception {
        EBWaitOnEnvironmentStatusStep step = new EBWaitOnEnvironmentStatusStep("my application", "my-environment");
        step.setStatus("SomeFutureStatus");
        EBWaitOnEnvironmentStatusStep.Execution execution = new EBWaitOnEnvironmentStatusStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .status("SomeFutureStatus")
                .build();
        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        when(client.describeEnvironments(any(DescribeEnvironmentsRequest.class))).thenReturn(result);

        execution.run();

        verify(client, times(1)).describeEnvironments(any(DescribeEnvironmentsRequest.class));
    }

	@Test
	void waitStopImmediatelyAfterFindingReadyStatus() throws Exception {
        EBWaitOnEnvironmentStatusStep step = new EBWaitOnEnvironmentStatusStep("my application", "my-environment");
        EBWaitOnEnvironmentStatusStep.Execution execution = new EBWaitOnEnvironmentStatusStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .status("Ready")
                .build();
        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        when(client.describeEnvironments(any(DescribeEnvironmentsRequest.class))).thenReturn(result);

        execution.run();

        verify(client, times(1)).describeEnvironments(describeCaptor.capture());
        assertEquals("my application", describeCaptor.getValue().applicationName());
        assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }
}
