package de.taimos.pipeline.aws.eb;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.SwapEnvironmentCnamEsRequest;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EBSwapEnvironmentCNAMEsStepTest {

    @Captor
    private ArgumentCaptor<SwapEnvironmentCnamEsRequest> captor;

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
        EBSwapEnvironmentCNAMEsStep.DescriptorImpl stepDescriptor = new EBSwapEnvironmentCNAMEsStep.DescriptorImpl();
        assertEquals("ebSwapEnvironmentCNAMEs", stepDescriptor.getFunctionName());
    }

	@Test
	void swapIsDoneWithDetailsProvided() throws Exception {
        EBSwapEnvironmentCNAMEsStep step = new EBSwapEnvironmentCNAMEsStep();
        step.setSourceEnvironmentId("source-id");
        step.setSourceEnvironmentName("source-name");
        step.setDestinationEnvironmentId("destination-id");
        step.setDestinationEnvironmentName("destination-name");
        EBSwapEnvironmentCNAMEsStep.Execution execution = new EBSwapEnvironmentCNAMEsStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        execution.run();

        verify(client, times(1)).swapEnvironmentCNAMEs(captor.capture());
        assertEquals("source-id", captor.getValue().sourceEnvironmentId());
        assertEquals("source-name", captor.getValue().sourceEnvironmentName());
        assertEquals("destination-id", captor.getValue().destinationEnvironmentId());
        assertEquals("destination-name", captor.getValue().destinationEnvironmentName());
    }

	@Test
	void swapCanBeDoneByCNAMELookup() throws Exception {
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
        when(client.describeEnvironments(any(DescribeEnvironmentsRequest.class))).thenReturn(result);


        execution.run();


        verify(client, times(1)).swapEnvironmentCNAMEs(captor.capture());
        assertEquals("source-id", captor.getValue().sourceEnvironmentId());
        assertEquals("source-name", captor.getValue().sourceEnvironmentName());
        assertEquals("destination-id", captor.getValue().destinationEnvironmentId());
        assertEquals("destination-name", captor.getValue().destinationEnvironmentName());
    }
}
