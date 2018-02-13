package de.taimos.pipeline.aws.cloudformation;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import de.taimos.pipeline.aws.AWSClientFactory;
import hudson.EnvVars;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
        value = AWSClientFactory.class,
        fullyQualifiedNames = "de.taimos.pipeline.aws.cloudformation.*"
)
@PowerMockIgnore("javax.crypto.*")
public class CFNDeleteStackTests {

    @Rule
    private JenkinsRule jenkinsRule = new JenkinsRule();
    private CloudFormationStack stack;
    private AmazonCloudFormation cloudFormation;

    @Before
    public void setupSdk() throws Exception {
        stack = Mockito.mock(CloudFormationStack.class);
        PowerMockito.mockStatic(AWSClientFactory.class);
        PowerMockito.whenNew(CloudFormationStack.class)
                .withAnyArguments()
                .thenReturn(stack);
        cloudFormation = Mockito.mock(AmazonCloudFormation.class);
        PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(EnvVars.class)))
                .thenReturn(cloudFormation);
    }

    @Test
    public void deleteStack() throws Exception {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
        job.setDefinition(new CpsFlowDefinition(""
                + "node {\n"
                + "  cfnDelete(stack: 'foo')"
                + "}\n", true)
        );
        jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

        PowerMockito.verifyNew(CloudFormationStack.class).withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
        Mockito.verify(stack).delete(Mockito.anyLong());
    }

}
