package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateConfigurationTemplateRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateConfigurationTemplateResponse;
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
public class EBCreateConfigurationTemplateStepTest {
    @Captor
    ArgumentCaptor<CreateConfigurationTemplateRequest> captor;

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

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateConfigurationTemplateResponse result = CreateConfigurationTemplateResponse.builder().build();
        Mockito.when(client.createConfigurationTemplate(Mockito.any(CreateConfigurationTemplateRequest.class))).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).createConfigurationTemplate(captor.capture());
        Assert.assertEquals("my application", captor.getValue().applicationName());
        Assert.assertEquals("my-template", captor.getValue().templateName());
        Assert.assertEquals("my-description", captor.getValue().description());
        Assert.assertEquals("my-environment", captor.getValue().environmentId());
        Assert.assertEquals("my-solution-stack", captor.getValue().solutionStackName());
        Assert.assertEquals("my-source-configuration-app", captor.getValue().sourceConfiguration().applicationName());
        Assert.assertEquals("my-source-configuration-template", captor.getValue().sourceConfiguration().templateName());
    }
}
