package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class EBWaitOnEnvironmentStatusStepTest {

    /**
     * These steps poll in an unbounded while(true) loop, so a regression in how the status is read
     * would hang the build instead of failing it. The timeout turns that back into a test failure.
     */
    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);
    @Captor
    ArgumentCaptor<DescribeEnvironmentsRequest> describeCaptor;

    private static StepContext context;

    @BeforeClass
    public static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

    @After
    public void resetClient() {
        // the factory delegate is static and would otherwise stay installed for whichever test
        // class runs next in the same JVM, handing it a mocked ElasticBeanstalkClient
        EBTestingUtils.resetElasticBeanstalkClient();
    }

    @Test
    public void stepDescriptorNameIsAsExpected() {
        EBWaitOnEnvironmentStatusStep.DescriptorImpl stepDescriptor = new EBWaitOnEnvironmentStatusStep.DescriptorImpl();
        Assert.assertEquals("ebWaitOnEnvironmentStatus", stepDescriptor.getFunctionName());
    }

    /**
     * "Ready" is a modelled EnvironmentStatus, so status().toString() would satisfy the test above
     * just as well as statusAsString(). The two only diverge for a value outside the enum - a
     * status AWS adds later - where the enum accessor yields UNKNOWN_TO_SDK_VERSION, rendering as
     * the literal "null", and this step's unbounded polling loop would never terminate: a hung
     * build rather than a failing one.
     */
    @Test
    public void waitStopsOnAStatusOutsideTheEnum() throws Exception {
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
        Mockito.when(client.describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class))).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class));
    }

    @Test
    public void waitStopImmediatelyAfterFindingReadyStatus() throws Exception {
        EBWaitOnEnvironmentStatusStep step = new EBWaitOnEnvironmentStatusStep("my application", "my-environment");
        EBWaitOnEnvironmentStatusStep.Execution execution = new EBWaitOnEnvironmentStatusStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .status("Ready")
                .build();
        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        Mockito.when(client.describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class))).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Assert.assertEquals("my application", describeCaptor.getValue().applicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }
}
