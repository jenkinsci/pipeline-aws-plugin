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
public class EBWaitOnEnvironmentHealthStepTest {

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
        EBWaitOnEnvironmentHealthStep.DescriptorImpl stepDescriptor = new EBWaitOnEnvironmentHealthStep.DescriptorImpl();
        Assert.assertEquals("ebWaitOnEnvironmentHealth", stepDescriptor.getFunctionName());
    }

    /**
     * "Green" is a modelled EnvironmentHealth, so health().toString() would pass the test below
     * too. Only a value outside the enum distinguishes healthAsString(), and there a regression
     * hangs this step's polling loop rather than failing it.
     */
    @Test
    public void waitStopsOnAHealthOutsideTheEnum() throws Exception {
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
        Mockito.when(client.describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class))).thenReturn(result);

        execution.run();

        // returning at all is the point - an enum accessor here would loop forever - but the poll
        // count is bounded too, so an extra round trip does not slip through unnoticed. With a
        // zero threshold the step returns on the first poll if a millisecond has already elapsed
        // and on the second otherwise, so the bound is at most two rather than a fixed count.
        Mockito.verify(client, Mockito.atMost(2)).describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class));
    }

    @Test
    public void waitStopImmediatelyAfterFindingGreenHealthForNoThreshold() throws Exception {
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
        Mockito.doReturn(result).when(client).describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class));

        execution.run();

        Mockito.verify(client, Mockito.atMost(2)).describeEnvironments(describeCaptor.capture());
        Assert.assertEquals("my application", describeCaptor.getValue().applicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }
}
