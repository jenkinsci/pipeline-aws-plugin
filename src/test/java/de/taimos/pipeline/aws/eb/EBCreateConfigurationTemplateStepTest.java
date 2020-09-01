package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.CreateConfigurationTemplateRequest;
import com.amazonaws.services.elasticbeanstalk.model.CreateConfigurationTemplateResult;
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
public class EBCreateConfigurationTemplateStepTest {
    @Captor
    ArgumentCaptor<CreateConfigurationTemplateRequest> captor;

    private static StepContext context;

    @BeforeClass
    public static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

    @Test
    public void stepDescriptorNameIsAsExpected() {
        EBCreateConfigurationTemplateStep.DescriptorImpl stepDescriptor = new EBCreateConfigurationTemplateStep.DescriptorImpl();
        Assert.assertEquals("ebCreateConfigurationTemplate", stepDescriptor.getFunctionName());
    }

    @Test
    public void templateIsCreatedWithDetailsProvided() throws Exception {
        EBCreateConfigurationTemplateStep step = new EBCreateConfigurationTemplateStep("my application", "my-template");
        step.setDescription("my-description");
        step.setEnvironmentId("my-environment");
        step.setSolutionStackName("my-solution-stack");
        step.setSourceConfigurationApplication("my-source-configuration-app");
        step.setSourceConfigurationTemplate("my-source-configuration-template");
        EBCreateConfigurationTemplateStep.Execution execution = new EBCreateConfigurationTemplateStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateConfigurationTemplateResult result = new CreateConfigurationTemplateResult();
        Mockito.when(client.createConfigurationTemplate(Mockito.any())).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).createConfigurationTemplate(captor.capture());
        Assert.assertEquals("my application", captor.getValue().getApplicationName());
        Assert.assertEquals("my-template", captor.getValue().getTemplateName());
        Assert.assertEquals("my-description", captor.getValue().getDescription());
        Assert.assertEquals("my-environment", captor.getValue().getEnvironmentId());
        Assert.assertEquals("my-solution-stack", captor.getValue().getSolutionStackName());
        Assert.assertEquals("my-source-configuration-app", captor.getValue().getSourceConfiguration().getApplicationName());
        Assert.assertEquals("my-source-configuration-template", captor.getValue().getSourceConfiguration().getTemplateName());
    }
}
