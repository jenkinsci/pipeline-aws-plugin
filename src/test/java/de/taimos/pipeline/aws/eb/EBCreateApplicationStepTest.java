package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationRequest;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationResult;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Assert;
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

    @Test
    public void stepDescriptorNameIsAsExpected() {
        EBCreateApplicationStep.DescriptorImpl stepDescriptor = new EBCreateApplicationStep.DescriptorImpl();
        Assert.assertEquals("ebCreateApplication", stepDescriptor.getFunctionName());
    }

    @Test
    public void applicationIsCreatedWithNameProvided() throws Exception {
        EBCreateApplicationStep step = new EBCreateApplicationStep("my application");
        EBCreateApplicationStep.Execution execution = new EBCreateApplicationStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateApplicationResult result = new CreateApplicationResult();
        result.setApplication(new ApplicationDescription());
        Mockito.when(client.createApplication(Mockito.any())).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).createApplication(captor.capture());
        Assert.assertEquals("my application", captor.getValue().getApplicationName());
    }
}
