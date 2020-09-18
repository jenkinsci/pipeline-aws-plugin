package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationVersionDescription;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationVersionResult;
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

@RunWith(PowerMockRunner.class)
@PrepareForTest(AWSElasticBeanstalkClientBuilder.class)
public class EBCreateApplicationVersionStepTest {
    @Captor
    ArgumentCaptor<CreateApplicationVersionRequest> captor;

    private static StepContext context;

    @BeforeClass
    public static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
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

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateApplicationVersionResult result = new CreateApplicationVersionResult();
        result.setApplicationVersion(new ApplicationVersionDescription());
        Mockito.when(client.createApplicationVersion(Mockito.any())).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).createApplicationVersion(captor.capture());
        Assert.assertEquals("my application", captor.getValue().getApplicationName());
        Assert.assertEquals("my version", captor.getValue().getVersionLabel());
        Assert.assertEquals("s3-bucket", captor.getValue().getSourceBundle().getS3Bucket());
        Assert.assertEquals("s3-key", captor.getValue().getSourceBundle().getS3Key());
    }
}
