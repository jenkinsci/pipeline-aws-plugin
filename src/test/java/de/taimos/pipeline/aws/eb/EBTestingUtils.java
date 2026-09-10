package de.taimos.pipeline.aws.eb;

import software.amazon.awssdk.services.elasticbeanstalk.ElasticBeanstalkClient;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.mockito.Mockito;

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

    static ElasticBeanstalkClient setupElasticBeanstalkClient() {
        ElasticBeanstalkClient client = Mockito.mock(ElasticBeanstalkClient.class);
        AWSClientFactory.setFactoryDelegate((x) -> client);
        return client;
    }

    static void resetElasticBeanstalkClient() {
        AWSClientFactory.setFactoryDelegate(null);
    }
}
