package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AWSElasticBeanstalkClientBuilder.class)
public class EBWaitOnEnvironmentHealthStepTest {
    @Captor
    ArgumentCaptor<DescribeEnvironmentsRequest> describeCaptor;

    private static StepContext context;

    @BeforeClass
    public static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

    @Test
    public void stepDescriptorNameIsAsExpected() {
        EBWaitOnEnvironmentHealthStep.DescriptorImpl stepDescriptor = new EBWaitOnEnvironmentHealthStep.DescriptorImpl();
        Assert.assertEquals("ebWaitOnEnvironmentHealth", stepDescriptor.getFunctionName());
    }

    @Test
    public void waitStopImmediatelyAfterFindingGreenHealthForNoThreshold() throws Exception {
        EBWaitOnEnvironmentHealthStep step = new EBWaitOnEnvironmentHealthStep("my application", "my-environment");
        step.setStabilityThreshold(0);
        EBWaitOnEnvironmentHealthStep.Execution execution = new EBWaitOnEnvironmentHealthStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        DescribeEnvironmentsResult result = new DescribeEnvironmentsResult();
        EnvironmentDescription environment = new EnvironmentDescription();
        environment.setHealth("Green");
        result.setEnvironments(Collections.singletonList(environment));
        Mockito.when(client.describeEnvironments(Mockito.any())).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.atMost(2)).describeEnvironments(describeCaptor.capture());
        Assert.assertEquals("my application", describeCaptor.getValue().getApplicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().getEnvironmentNames().get(0));
    }
}
