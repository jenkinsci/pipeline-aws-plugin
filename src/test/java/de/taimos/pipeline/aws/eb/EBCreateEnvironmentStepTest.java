package de.taimos.pipeline.aws.eb;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateEnvironmentRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateEnvironmentResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.UpdateEnvironmentRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.UpdateEnvironmentResponse;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EBCreateEnvironmentStepTest {

    @Captor
    private ArgumentCaptor<CreateEnvironmentRequest> captor;
    @Captor
    private ArgumentCaptor<DescribeEnvironmentsRequest> describeCaptor;
    @Captor
    private ArgumentCaptor<UpdateEnvironmentRequest> updateCaptor;

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
        EBCreateEnvironmentStep.DescriptorImpl stepDescriptor = new EBCreateEnvironmentStep.DescriptorImpl();
        assertEquals("ebCreateEnvironment", stepDescriptor.getFunctionName());
    }

	@Test
	void environmentIsCreatedWithDetailsProvided() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        step.setDescription("my-description");
        step.setTemplateName("my-template");
        step.setSolutionStackName("my-solution-stack");
        step.setVersionLabel("my-version");
        step.setUpdateOnExisting(false);
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateEnvironmentResponse result = CreateEnvironmentResponse.builder().build();
        doReturn(result).when(client).createEnvironment(any(CreateEnvironmentRequest.class));

        execution.run();

        verify(client, times(0)).describeEnvironments(any(DescribeEnvironmentsRequest.class));
        verify(client, times(0)).updateEnvironment(any(UpdateEnvironmentRequest.class));
        verify(client, times(1)).createEnvironment(captor.capture());
        assertEquals("my application", captor.getValue().applicationName());
        assertEquals("my-template", captor.getValue().templateName());
        assertEquals("my-description", captor.getValue().description());
        assertEquals("my-environment", captor.getValue().environmentName());
        assertEquals("my-solution-stack", captor.getValue().solutionStackName());
        assertEquals("my-version", captor.getValue().versionLabel());
    }

	@Test
	void environmentIsUpdatedIfExisting() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        step.setDescription("my-description");
        step.setTemplateName("my-template");
        step.setSolutionStackName("my-solution-stack");
        step.setVersionLabel("my-version");
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder().status("Ready").build();
        DescribeEnvironmentsResponse describeResult = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        doReturn(describeResult).when(client).describeEnvironments(any(DescribeEnvironmentsRequest.class));

        UpdateEnvironmentResponse updateResult = UpdateEnvironmentResponse.builder().build();
        doReturn(updateResult).when(client).updateEnvironment(any(UpdateEnvironmentRequest.class));

        execution.run();

        verify(client, times(1)).describeEnvironments(describeCaptor.capture());
        verify(client, times(1)).updateEnvironment(updateCaptor.capture());
        verify(client, times(0)).createEnvironment(any(CreateEnvironmentRequest.class));
        assertEquals("my application", updateCaptor.getValue().applicationName());
        assertEquals("my-template", updateCaptor.getValue().templateName());
        assertEquals("my-description", updateCaptor.getValue().description());
        assertEquals("my-environment", updateCaptor.getValue().environmentName());
        assertEquals("my-solution-stack", updateCaptor.getValue().solutionStackName());
        assertEquals("my-version", updateCaptor.getValue().versionLabel());

        assertEquals("my application", describeCaptor.getValue().applicationName());
        assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }

	@Test
	void environmentIsCreatedIfNotExisting() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        DescribeEnvironmentsResponse describeResult = DescribeEnvironmentsResponse.builder().build();
        when(client.describeEnvironments(any(DescribeEnvironmentsRequest.class))).thenReturn(describeResult);

        CreateEnvironmentResponse result = CreateEnvironmentResponse.builder().build();
        when(client.createEnvironment(any(CreateEnvironmentRequest.class))).thenReturn(result);

        execution.run();

        verify(client, times(1)).describeEnvironments(describeCaptor.capture());
        verify(client, times(0)).updateEnvironment(any(UpdateEnvironmentRequest.class));
        verify(client, times(1)).createEnvironment(any(CreateEnvironmentRequest.class));

        assertEquals("my application", describeCaptor.getValue().applicationName());
        assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }

	@Test
	void terminatedEnvironmentsAreNonExisting() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .status("Terminated")
                .build();
        DescribeEnvironmentsResponse describeResult = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        doReturn(describeResult).when(client).describeEnvironments(any(DescribeEnvironmentsRequest.class));

        CreateEnvironmentResponse result = CreateEnvironmentResponse.builder().build();
        when(client.createEnvironment(any(CreateEnvironmentRequest.class))).thenReturn(result);

        execution.run();

        verify(client, times(1)).describeEnvironments(describeCaptor.capture());
        verify(client, times(0)).updateEnvironment(any(UpdateEnvironmentRequest.class));
        verify(client, times(1)).createEnvironment(any(CreateEnvironmentRequest.class));

        assertEquals("my application", describeCaptor.getValue().applicationName());
        assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }
}
