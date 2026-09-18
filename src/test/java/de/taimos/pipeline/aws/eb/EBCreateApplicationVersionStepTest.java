package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.ApplicationVersionDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationVersionResponse;
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
public class EBCreateApplicationVersionStepTest {
    @Captor
    ArgumentCaptor<CreateApplicationVersionRequest> captor;

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
        EBCreateApplicationVersionStep.DescriptorImpl stepDescriptor = new EBCreateApplicationVersionStep.DescriptorImpl();
        Assert.assertEquals("ebCreateApplicationVersion", stepDescriptor.getFunctionName());
    }

    @Test
    public void applicationVersionIsCreatedWithDetailsProvided() throws Exception {
        EBCreateApplicationVersionStep step = new EBCreateApplicationVersionStep(
                "my application",
                "my version",
                "s3-bucket",
                "s3-key"
        );
        EBCreateApplicationVersionStep.Execution execution = new EBCreateApplicationVersionStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateApplicationVersionResponse result = CreateApplicationVersionResponse.builder()
                .applicationVersion(ApplicationVersionDescription.builder().build())
                .build();
        Mockito.when(client.createApplicationVersion(Mockito.any(CreateApplicationVersionRequest.class))).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).createApplicationVersion(captor.capture());
        Assert.assertEquals("my application", captor.getValue().applicationName());
        Assert.assertEquals("my version", captor.getValue().versionLabel());
        Assert.assertEquals("s3-bucket", captor.getValue().sourceBundle().s3Bucket());
        Assert.assertEquals("s3-key", captor.getValue().sourceBundle().s3Key());
    }
}
