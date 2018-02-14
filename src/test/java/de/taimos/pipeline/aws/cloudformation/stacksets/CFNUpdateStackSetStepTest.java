package de.taimos.pipeline.aws.cloudformation.stacksets;

import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.cloudformation.model.UpdateStackSetResult;
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

import java.util.Collections;
import java.util.UUID;

@RunWith(PowerMockRunner.class)
@PrepareForTest(
		value = AWSClientFactory.class,
		fullyQualifiedNames = "de.taimos.pipeline.aws.cloudformation.stacksets.*"
)
@PowerMockIgnore("javax.crypto.*")
public class CFNUpdateStackSetStepTest {

	@Rule
	private JenkinsRule jenkinsRule = new JenkinsRule();
	private CloudFormationStackSet stackSet;

	@Before
	public void setupSdk() throws Exception {
		stackSet = Mockito.mock(CloudFormationStackSet.class);
		PowerMockito.mockStatic(AWSClientFactory.class);
		PowerMockito.whenNew(CloudFormationStackSet.class)
				.withAnyArguments()
				.thenReturn(stackSet);
		AmazonCloudFormation cloudFormation = Mockito.mock(AmazonCloudFormation.class);
		PowerMockito.when(AWSClientFactory.create(Mockito.any(AwsSyncClientBuilder.class), Mockito.any(EnvVars.class)))
				.thenReturn(cloudFormation);
	}

	@Test
	public void createNonExistantStack() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
		Mockito.when(stackSet.exists()).thenReturn(false);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfnUpdateStackSet(stackSet: 'foo')"
				+ "}\n", true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		PowerMockito.verifyNew(CloudFormationStackSet.class, Mockito.atLeastOnce())
				.withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(stackSet).create(Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection());
	}

	@Test
	public void updateExistantStack() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stackSet.exists()).thenReturn(true);
		String operationId = UUID.randomUUID().toString();
		Mockito.when(stackSet.update(Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection()))
				.thenReturn(new UpdateStackSetResult()
						.withOperationId(operationId)
				);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfnUpdateStackSet(stackSet: 'foo', pollInterval: 27, params: ['foo=bar'])"
				+ "}\n", true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		PowerMockito.verifyNew(CloudFormationStackSet.class, Mockito.atLeastOnce())
				.withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(stackSet).update(Mockito.anyString(), Mockito.anyString(), Mockito.eq(Collections.singletonList(new Parameter()
				.withParameterKey("foo")
				.withParameterValue("bar")
		)), Mockito.<Tag>anyCollection());
		Mockito.verify(stackSet).waitForOperationToComplete(operationId, 27);
	}

	@Test
	public void doNotCreateNonExistantStack() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		Mockito.when(stackSet.exists()).thenReturn(false);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  cfnUpdateStackSet(stackSet: 'foo', create: false)"
				+ "}\n", true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		PowerMockito.verifyNew(CloudFormationStackSet.class)
				.withArguments(Mockito.any(AmazonCloudFormation.class), Mockito.eq("foo"), Mockito.any(TaskListener.class));
		Mockito.verify(stackSet, Mockito.never()).create(Mockito.anyString(), Mockito.anyString(), Mockito.<Parameter>anyCollection(), Mockito.<Tag>anyCollection());
	}
}
