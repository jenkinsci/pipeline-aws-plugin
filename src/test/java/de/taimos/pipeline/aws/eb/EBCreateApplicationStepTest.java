package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.ApplicationDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationResponse;
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

@RunWith(MockitoJUnitRunner.class)
public class EBCreateApplicationStepTest {
    @Captor
    ArgumentCaptor<CreateApplicationRequest> captor;

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
        EBCreateApplicationStep.DescriptorImpl stepDescriptor = new EBCreateApplicationStep.DescriptorImpl();
        Assert.assertEquals("ebCreateApplication", stepDescriptor.getFunctionName());
    }

    @Test
    public void applicationIsCreatedWithNameProvided() throws Exception {
        EBCreateApplicationStep step = new EBCreateApplicationStep("my application");
        EBCreateApplicationStep.Execution execution = new EBCreateApplicationStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateApplicationResponse result = CreateApplicationResponse.builder()
                .application(ApplicationDescription.builder().build())
                .build();
        Mockito.when(client.createApplication(Mockito.any(CreateApplicationRequest.class))).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).createApplication(captor.capture());
        Assert.assertEquals("my application", captor.getValue().applicationName());
    }
}
