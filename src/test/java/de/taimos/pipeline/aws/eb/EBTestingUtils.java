package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;

import java.io.PrintStream;

class EBTestingUtils {

    static StepContext setupStepContext() throws Exception {
        StepContext context = Mockito.mock(StepContext.class);
        TaskListener listener = Mockito.mock(TaskListener.class);
        Mockito.when(listener.getLogger()).thenReturn(Mockito.mock(PrintStream.class));
        Mockito.when(context.get(TaskListener.class)).thenReturn(listener);
        Mockito.when(context.get(EnvVars.class)).thenReturn(new EnvVars());
        return context;
    }

    static AWSElasticBeanstalk setupElasticBeanstalkClient() {
        PowerMockito.mockStatic(AWSClientFactory.class);
        AWSElasticBeanstalk client = Mockito.mock(AWSElasticBeanstalk.class);
        PowerMockito.when(AWSClientFactory.create(
                Mockito.any(AWSElasticBeanstalkClientBuilder.class),
                Mockito.any(StepContext.class),
                Mockito.any(EnvVars.class))
        ).thenReturn(client);
        return client;
    }
}
