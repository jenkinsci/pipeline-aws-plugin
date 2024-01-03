package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.SwapEnvironmentCNAMEsRequest;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

@RunWith(MockitoJUnitRunner.class)
public class EBSwapEnvironmentCNAMEsStepTest {
    @Captor
    ArgumentCaptor<SwapEnvironmentCNAMEsRequest> captor;

    private static StepContext context;

    @BeforeClass
    public static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

    @Test
    public void stepDescriptorNameIsAsExpected() {
        EBSwapEnvironmentCNAMEsStep.DescriptorImpl stepDescriptor = new EBSwapEnvironmentCNAMEsStep.DescriptorImpl();
        Assert.assertEquals("ebSwapEnvironmentCNAMEs", stepDescriptor.getFunctionName());
    }

    @Test
    public void swapIsDoneWithDetailsProvided() throws Exception {
        EBSwapEnvironmentCNAMEsStep step = new EBSwapEnvironmentCNAMEsStep();
        step.setSourceEnvironmentId("source-id");
        step.setSourceEnvironmentName("source-name");
        step.setDestinationEnvironmentId("destination-id");
        step.setDestinationEnvironmentName("destination-name");
        EBSwapEnvironmentCNAMEsStep.Execution execution = new EBSwapEnvironmentCNAMEsStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        execution.run();

        Mockito.verify(client, Mockito.times(1)).swapEnvironmentCNAMEs(captor.capture());
        Assert.assertEquals("source-id", captor.getValue().getSourceEnvironmentId());
        Assert.assertEquals("source-name", captor.getValue().getSourceEnvironmentName());
        Assert.assertEquals("destination-id", captor.getValue().getDestinationEnvironmentId());
        Assert.assertEquals("destination-name", captor.getValue().getDestinationEnvironmentName());
    }

    @Test
    public void swapCanBeDoneByCNAMELookup() throws Exception {
        EBSwapEnvironmentCNAMEsStep step = new EBSwapEnvironmentCNAMEsStep();
        step.setSourceEnvironmentCNAME("source-cname");
        step.setDestinationEnvironmentCNAME("destination-cname");
        EBSwapEnvironmentCNAMEsStep.Execution execution = new EBSwapEnvironmentCNAMEsStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        DescribeEnvironmentsResult result = new DescribeEnvironmentsResult();
        EnvironmentDescription sourceEnv = new EnvironmentDescription();
        sourceEnv.setCNAME("source-cname");
        sourceEnv.setEnvironmentId("source-id");
        sourceEnv.setEnvironmentName("source-name");

        EnvironmentDescription destinationEnv = new EnvironmentDescription();
        destinationEnv.setCNAME("destination-cname");
        destinationEnv.setEnvironmentId("destination-id");
        destinationEnv.setEnvironmentName("destination-name");

        result.setEnvironments(Arrays.asList(sourceEnv, destinationEnv));
        Mockito.when(client.describeEnvironments(Mockito.any())).thenReturn(result);


        execution.run();


        Mockito.verify(client, Mockito.times(1)).swapEnvironmentCNAMEs(captor.capture());
        Assert.assertEquals("source-id", captor.getValue().getSourceEnvironmentId());
        Assert.assertEquals("source-name", captor.getValue().getSourceEnvironmentName());
        Assert.assertEquals("destination-id", captor.getValue().getDestinationEnvironmentId());
        Assert.assertEquals("destination-name", captor.getValue().getDestinationEnvironmentName());
    }
}
