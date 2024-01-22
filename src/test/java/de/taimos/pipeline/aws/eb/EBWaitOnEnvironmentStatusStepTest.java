package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
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
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class EBWaitOnEnvironmentStatusStepTest {
    @Captor
    ArgumentCaptor<DescribeEnvironmentsRequest> describeCaptor;

    private static StepContext context;

    @BeforeClass
    public static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

    @Test
    public void stepDescriptorNameIsAsExpected() {
        EBWaitOnEnvironmentStatusStep.DescriptorImpl stepDescriptor = new EBWaitOnEnvironmentStatusStep.DescriptorImpl();
        Assert.assertEquals("ebWaitOnEnvironmentStatus", stepDescriptor.getFunctionName());
    }

    @Test
    public void waitStopImmediatelyAfterFindingReadyStatus() throws Exception {
        EBWaitOnEnvironmentStatusStep step = new EBWaitOnEnvironmentStatusStep("my application", "my-environment");
        EBWaitOnEnvironmentStatusStep.Execution execution = new EBWaitOnEnvironmentStatusStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        DescribeEnvironmentsResult result = new DescribeEnvironmentsResult();
        EnvironmentDescription environment = new EnvironmentDescription();
        environment.setStatus("Ready");
        result.setEnvironments(Collections.singletonList(environment));
        Mockito.when(client.describeEnvironments(Mockito.any())).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Assert.assertEquals("my application", describeCaptor.getValue().getApplicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().getEnvironmentNames().get(0));
    }
}
