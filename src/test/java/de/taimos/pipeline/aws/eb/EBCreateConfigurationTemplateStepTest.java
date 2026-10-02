package de.taimos.pipeline.aws.eb;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateConfigurationTemplateRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateConfigurationTemplateResponse;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@ExtendWith(MockitoExtension.class)
class EBCreateConfigurationTemplateStepTest {

    @Captor
    private ArgumentCaptor<CreateConfigurationTemplateRequest> captor;

    private static StepContext context;

	@BeforeAll
	static void setupStepContext() throws Exception {
        context = EBTestingUtils.setupStepContext();
    }

	@AfterEach
	void resetClient() {
        // the factory delegate is static and would otherwise stay installed for whichever test
        // class runs next in the same JVM, handing it a mocked ElasticBeanstalkClient
        EBTestingUtils.resetElasticBeanstalkClient();
    }

	@Test
	void stepDescriptorNameIsAsExpected() {
        EBCreateConfigurationTemplateStep.DescriptorImpl stepDescriptor = new EBCreateConfigurationTemplateStep.DescriptorImpl();
        assertEquals("ebCreateConfigurationTemplate", stepDescriptor.getFunctionName());
    }

	@Test
	void templateIsCreatedWithDetailsProvided() throws Exception {
        EBCreateConfigurationTemplateStep step = new EBCreateConfigurationTemplateStep("my application", "my-template");
        step.setDescription("my-description");
        step.setEnvironmentId("my-environment");
        step.setSolutionStackName("my-solution-stack");
        step.setSourceConfigurationApplication("my-source-configuration-app");
        step.setSourceConfigurationTemplate("my-source-configuration-template");
        EBCreateConfigurationTemplateStep.Execution execution = new EBCreateConfigurationTemplateStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateConfigurationTemplateResponse result = CreateConfigurationTemplateResponse.builder().build();
        when(client.createConfigurationTemplate(any(CreateConfigurationTemplateRequest.class))).thenReturn(result);

        execution.run();

        verify(client, times(1)).createConfigurationTemplate(captor.capture());
        assertEquals("my application", captor.getValue().applicationName());
        assertEquals("my-template", captor.getValue().templateName());
        assertEquals("my-description", captor.getValue().description());
        assertEquals("my-environment", captor.getValue().environmentId());
        assertEquals("my-solution-stack", captor.getValue().solutionStackName());
        assertEquals("my-source-configuration-app", captor.getValue().sourceConfiguration().applicationName());
        assertEquals("my-source-configuration-template", captor.getValue().sourceConfiguration().templateName());
    }
}
