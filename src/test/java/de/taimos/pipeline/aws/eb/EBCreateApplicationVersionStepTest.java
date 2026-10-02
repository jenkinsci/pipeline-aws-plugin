package de.taimos.pipeline.aws.eb;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.ApplicationVersionDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationVersionRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateApplicationVersionResponse;
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
class EBCreateApplicationVersionStepTest {

    @Captor
    private ArgumentCaptor<CreateApplicationVersionRequest> captor;

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
        EBCreateApplicationVersionStep.DescriptorImpl stepDescriptor = new EBCreateApplicationVersionStep.DescriptorImpl();
        assertEquals("ebCreateApplicationVersion", stepDescriptor.getFunctionName());
    }

	@Test
	void applicationVersionIsCreatedWithDetailsProvided() throws Exception {
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
        when(client.createApplicationVersion(any(CreateApplicationVersionRequest.class))).thenReturn(result);

        execution.run();

        verify(client, times(1)).createApplicationVersion(captor.capture());
        assertEquals("my application", captor.getValue().applicationName());
        assertEquals("my version", captor.getValue().versionLabel());
        assertEquals("s3-bucket", captor.getValue().sourceBundle().s3Bucket());
        assertEquals("s3-key", captor.getValue().sourceBundle().s3Key());
    }
}
