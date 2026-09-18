package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.SwapEnvironmentCnamEsRequest;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
import org.junit.After;
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
    ArgumentCaptor<SwapEnvironmentCnamEsRequest> captor;

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

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        execution.run();

        Mockito.verify(client, Mockito.times(1)).swapEnvironmentCNAMEs(captor.capture());
        Assert.assertEquals("source-id", captor.getValue().sourceEnvironmentId());
        Assert.assertEquals("source-name", captor.getValue().sourceEnvironmentName());
        Assert.assertEquals("destination-id", captor.getValue().destinationEnvironmentId());
        Assert.assertEquals("destination-name", captor.getValue().destinationEnvironmentName());
    }

    @Test
    public void swapCanBeDoneByCNAMELookup() throws Exception {
        EBSwapEnvironmentCNAMEsStep step = new EBSwapEnvironmentCNAMEsStep();
        step.setSourceEnvironmentCNAME("source-cname");
        step.setDestinationEnvironmentCNAME("destination-cname");
        EBSwapEnvironmentCNAMEsStep.Execution execution = new EBSwapEnvironmentCNAMEsStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription sourceEnv = EnvironmentDescription.builder()
                .cname("source-cname")
                .environmentId("source-id")
                .environmentName("source-name")
                .build();

        EnvironmentDescription destinationEnv = EnvironmentDescription.builder()
                .cname("destination-cname")
                .environmentId("destination-id")
                .environmentName("destination-name")
                .build();

        DescribeEnvironmentsResponse result = DescribeEnvironmentsResponse.builder()
                .environments(Arrays.asList(sourceEnv, destinationEnv))
                .build();
        Mockito.when(client.describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class))).thenReturn(result);


        execution.run();


        Mockito.verify(client, Mockito.times(1)).swapEnvironmentCNAMEs(captor.capture());
        Assert.assertEquals("source-id", captor.getValue().sourceEnvironmentId());
        Assert.assertEquals("source-name", captor.getValue().sourceEnvironmentName());
        Assert.assertEquals("destination-id", captor.getValue().destinationEnvironmentId());
        Assert.assertEquals("destination-name", captor.getValue().destinationEnvironmentName());
    }
}
