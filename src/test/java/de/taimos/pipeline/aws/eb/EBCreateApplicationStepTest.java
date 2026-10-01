package de.taimos.pipeline.aws.eb;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.ApplicationDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationResponse;
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
class EBCreateApplicationStepTest {

    @Captor
    private ArgumentCaptor<CreateApplicationRequest> captor;

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
        EBCreateApplicationStep.DescriptorImpl stepDescriptor = new EBCreateApplicationStep.DescriptorImpl();
        assertEquals("ebCreateApplication", stepDescriptor.getFunctionName());
    }

	@Test
	void applicationIsCreatedWithNameProvided() throws Exception {
        EBCreateApplicationStep step = new EBCreateApplicationStep("my application");
        EBCreateApplicationStep.Execution execution = new EBCreateApplicationStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateApplicationResponse result = CreateApplicationResponse.builder()
                .application(ApplicationDescription.builder().build())
                .build();
        when(client.createApplication(any(CreateApplicationRequest.class))).thenReturn(result);

        execution.run();

        verify(client, times(1)).createApplication(captor.capture());
        assertEquals("my application", captor.getValue().applicationName());
    }
}
