package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateEnvironmentRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.CreateEnvironmentResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.DescribeEnvironmentsResponse;
import software.amazon.awssdk.services.elasticbeanstalk.model.EnvironmentDescription;
import software.amazon.awssdk.services.elasticbeanstalk.model.UpdateEnvironmentRequest;
import software.amazon.awssdk.services.elasticbeanstalk.model.UpdateEnvironmentResponse;
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

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class EBCreateEnvironmentStepTest {
    @Captor
    ArgumentCaptor<CreateEnvironmentRequest> captor;
    @Captor
    ArgumentCaptor<DescribeEnvironmentsRequest> describeCaptor;
    @Captor
    ArgumentCaptor<UpdateEnvironmentRequest> updateCaptor;

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
        EBCreateEnvironmentStep.DescriptorImpl stepDescriptor = new EBCreateEnvironmentStep.DescriptorImpl();
        Assert.assertEquals("ebCreateEnvironment", stepDescriptor.getFunctionName());
    }

    @Test
    public void environmentIsCreatedWithDetailsProvided() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        step.setDescription("my-description");
        step.setTemplateName("my-template");
        step.setSolutionStackName("my-solution-stack");
        step.setVersionLabel("my-version");
        step.setUpdateOnExisting(false);
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateEnvironmentResponse result = CreateEnvironmentResponse.builder().build();
        Mockito.doReturn(result).when(client).createEnvironment(Mockito.any(CreateEnvironmentRequest.class));

        execution.run();

        Mockito.verify(client, Mockito.times(0)).describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class));
        Mockito.verify(client, Mockito.times(0)).updateEnvironment(Mockito.any(UpdateEnvironmentRequest.class));
        Mockito.verify(client, Mockito.times(1)).createEnvironment(captor.capture());
        Assert.assertEquals("my application", captor.getValue().applicationName());
        Assert.assertEquals("my-template", captor.getValue().templateName());
        Assert.assertEquals("my-description", captor.getValue().description());
        Assert.assertEquals("my-environment", captor.getValue().environmentName());
        Assert.assertEquals("my-solution-stack", captor.getValue().solutionStackName());
        Assert.assertEquals("my-version", captor.getValue().versionLabel());
    }

    @Test
    public void environmentIsUpdatedIfExisting() throws Exception {
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
        Mockito.doReturn(describeResult).when(client).describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class));

        UpdateEnvironmentResponse updateResult = UpdateEnvironmentResponse.builder().build();
        Mockito.doReturn(updateResult).when(client).updateEnvironment(Mockito.any(UpdateEnvironmentRequest.class));

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Mockito.verify(client, Mockito.times(1)).updateEnvironment(updateCaptor.capture());
        Mockito.verify(client, Mockito.times(0)).createEnvironment(Mockito.any(CreateEnvironmentRequest.class));
        Assert.assertEquals("my application", updateCaptor.getValue().applicationName());
        Assert.assertEquals("my-template", updateCaptor.getValue().templateName());
        Assert.assertEquals("my-description", updateCaptor.getValue().description());
        Assert.assertEquals("my-environment", updateCaptor.getValue().environmentName());
        Assert.assertEquals("my-solution-stack", updateCaptor.getValue().solutionStackName());
        Assert.assertEquals("my-version", updateCaptor.getValue().versionLabel());

        Assert.assertEquals("my application", describeCaptor.getValue().applicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }

    @Test
    public void environmentIsCreatedIfNotExisting() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        DescribeEnvironmentsResponse describeResult = DescribeEnvironmentsResponse.builder().build();
        Mockito.when(client.describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class))).thenReturn(describeResult);

        CreateEnvironmentResponse result = CreateEnvironmentResponse.builder().build();
        Mockito.when(client.createEnvironment(Mockito.any(CreateEnvironmentRequest.class))).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Mockito.verify(client, Mockito.times(0)).updateEnvironment(Mockito.any(UpdateEnvironmentRequest.class));
        Mockito.verify(client, Mockito.times(1)).createEnvironment(Mockito.any(CreateEnvironmentRequest.class));

        Assert.assertEquals("my application", describeCaptor.getValue().applicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }

    @Test
    public void terminatedEnvironmentsAreNonExisting() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        ElasticBeanstalkClient client = EBTestingUtils.setupElasticBeanstalkClient();
        EnvironmentDescription environment = EnvironmentDescription.builder()
                .status("Terminated")
                .build();
        DescribeEnvironmentsResponse describeResult = DescribeEnvironmentsResponse.builder()
                .environments(Collections.singletonList(environment))
                .build();
        Mockito.doReturn(describeResult).when(client).describeEnvironments(Mockito.any(DescribeEnvironmentsRequest.class));

        CreateEnvironmentResponse result = CreateEnvironmentResponse.builder().build();
        Mockito.when(client.createEnvironment(Mockito.any(CreateEnvironmentRequest.class))).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Mockito.verify(client, Mockito.times(0)).updateEnvironment(Mockito.any(UpdateEnvironmentRequest.class));
        Mockito.verify(client, Mockito.times(1)).createEnvironment(Mockito.any(CreateEnvironmentRequest.class));

        Assert.assertEquals("my application", describeCaptor.getValue().applicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().environmentNames().get(0));
    }
}
