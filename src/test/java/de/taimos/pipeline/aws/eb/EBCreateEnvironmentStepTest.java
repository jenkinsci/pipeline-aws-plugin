package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentRequest;
import com.amazonaws.services.elasticbeanstalk.model.CreateEnvironmentResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentRequest;
import com.amazonaws.services.elasticbeanstalk.model.UpdateEnvironmentResult;
import de.taimos.pipeline.aws.AWSClientFactory;
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

import java.util.Collections;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AWSClientFactory.class)
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

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        CreateEnvironmentResult result = new CreateEnvironmentResult();
        Mockito.when(client.createEnvironment(Mockito.any())).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(0)).describeEnvironments(Mockito.any());
        Mockito.verify(client, Mockito.times(0)).updateEnvironment(Mockito.any());
        Mockito.verify(client, Mockito.times(1)).createEnvironment(captor.capture());
        Assert.assertEquals("my application", captor.getValue().getApplicationName());
        Assert.assertEquals("my-template", captor.getValue().getTemplateName());
        Assert.assertEquals("my-description", captor.getValue().getDescription());
        Assert.assertEquals("my-environment", captor.getValue().getEnvironmentName());
        Assert.assertEquals("my-solution-stack", captor.getValue().getSolutionStackName());
        Assert.assertEquals("my-version", captor.getValue().getVersionLabel());
    }

    @Test
    public void environmentIsUpdatedIfExisting() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        step.setDescription("my-description");
        step.setTemplateName("my-template");
        step.setSolutionStackName("my-solution-stack");
        step.setVersionLabel("my-version");
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        DescribeEnvironmentsResult describeResult = new DescribeEnvironmentsResult();
        EnvironmentDescription environment = new EnvironmentDescription();
        environment.setStatus("Ready");
        describeResult.setEnvironments(Collections.singletonList(environment));
        Mockito.when(client.describeEnvironments(Mockito.any())).thenReturn(describeResult);

        UpdateEnvironmentResult updateResult = new UpdateEnvironmentResult();
        Mockito.when(client.updateEnvironment(Mockito.any())).thenReturn(updateResult);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Mockito.verify(client, Mockito.times(1)).updateEnvironment(updateCaptor.capture());
        Mockito.verify(client, Mockito.times(0)).createEnvironment(Mockito.any());
        Assert.assertEquals("my application", updateCaptor.getValue().getApplicationName());
        Assert.assertEquals("my-template", updateCaptor.getValue().getTemplateName());
        Assert.assertEquals("my-description", updateCaptor.getValue().getDescription());
        Assert.assertEquals("my-environment", updateCaptor.getValue().getEnvironmentName());
        Assert.assertEquals("my-solution-stack", updateCaptor.getValue().getSolutionStackName());
        Assert.assertEquals("my-version", updateCaptor.getValue().getVersionLabel());

        Assert.assertEquals("my application", describeCaptor.getValue().getApplicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().getEnvironmentNames().get(0));
    }

    @Test
    public void environmentIsCreatedIfNotExisting() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        DescribeEnvironmentsResult describeResult = new DescribeEnvironmentsResult();
        Mockito.when(client.describeEnvironments(Mockito.any())).thenReturn(describeResult);

        CreateEnvironmentResult result = new CreateEnvironmentResult();
        Mockito.when(client.createEnvironment(Mockito.any())).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Mockito.verify(client, Mockito.times(0)).updateEnvironment(Mockito.any());
        Mockito.verify(client, Mockito.times(1)).createEnvironment(Mockito.any());

        Assert.assertEquals("my application", describeCaptor.getValue().getApplicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().getEnvironmentNames().get(0));
    }

    @Test
    public void terminatedEnvironmentsAreNonExisting() throws Exception {
        EBCreateEnvironmentStep step = new EBCreateEnvironmentStep("my application", "my-environment");
        EBCreateEnvironmentStep.Execution execution = new EBCreateEnvironmentStep.Execution(step, context);

        AWSElasticBeanstalk client = EBTestingUtils.setupElasticBeanstalkClient();
        DescribeEnvironmentsResult describeResult = new DescribeEnvironmentsResult();
        EnvironmentDescription environment = new EnvironmentDescription();
        environment.setStatus("Terminated");
        describeResult.setEnvironments(Collections.singletonList(environment));
        Mockito.when(client.describeEnvironments(Mockito.any())).thenReturn(describeResult);

        CreateEnvironmentResult result = new CreateEnvironmentResult();
        Mockito.when(client.createEnvironment(Mockito.any())).thenReturn(result);

        execution.run();

        Mockito.verify(client, Mockito.times(1)).describeEnvironments(describeCaptor.capture());
        Mockito.verify(client, Mockito.times(0)).updateEnvironment(Mockito.any());
        Mockito.verify(client, Mockito.times(1)).createEnvironment(Mockito.any());

        Assert.assertEquals("my application", describeCaptor.getValue().getApplicationName());
        Assert.assertEquals("my-environment", describeCaptor.getValue().getEnvironmentNames().get(0));
    }
}
