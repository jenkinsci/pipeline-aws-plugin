package de.taimos.pipeline.aws.eb;

import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClientBuilder;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;
import com.amazonaws.services.elasticbeanstalk.model.CreateApplicationResult;
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
        Mockito.when(context.get(Mockito.any())).thenReturn(listener);
        return context;
    }

    static AWSElasticBeanstalk setupElasticBeanstalkClient() {
        PowerMockito.mockStatic(AWSElasticBeanstalkClientBuilder.class);
        AWSElasticBeanstalk client = Mockito.mock(AWSElasticBeanstalk.class);
        PowerMockito.when(AWSElasticBeanstalkClientBuilder.defaultClient()).thenReturn(client);
        return client;
    }
}
