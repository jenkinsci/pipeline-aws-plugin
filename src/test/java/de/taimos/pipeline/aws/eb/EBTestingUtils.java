package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.PrintStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EBTestingUtils {

    static StepContext setupStepContext() throws Exception {
        StepContext context = mock(StepContext.class);
        TaskListener listener = mock(TaskListener.class);
        when(listener.getLogger()).thenReturn(mock(PrintStream.class));
        when(context.get(TaskListener.class)).thenReturn(listener);
        when(context.get(EnvVars.class)).thenReturn(new EnvVars());
        return context;
    }

    static ElasticBeanstalkClient setupElasticBeanstalkClient() {
        ElasticBeanstalkClient client = mock(ElasticBeanstalkClient.class);
        AWSClientFactory.setFactoryDelegate(x -> client);
        return client;
    }

    static void resetElasticBeanstalkClient() {
        AWSClientFactory.setFactoryDelegate(null);
    }
}
