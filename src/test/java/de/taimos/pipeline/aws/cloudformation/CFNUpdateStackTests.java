package de.taimos.pipeline.aws.cloudformation;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class CFNUpdateStackTests {

	private JenkinsRule jenkinsRule;
	private CloudFormationStack stack;
	private CloudFormationClient cloudFormation;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.stack = mock(CloudFormationStack.class);
		this.cloudFormation = mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate(x -> this.cloudFormation);
		AWSUtilFactory.setStackSupplier(s -> {
			assertEquals("foo", s);
			return stack;
		});
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
		AWSUtilFactory.setStackSupplier(null);
	}

	@Test
	void createNonExistantStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
		when(this.stack.exists()).thenReturn(false);
		when(this.stack.describeOutputs()).thenReturn(Collections.singletonMap("foo", "bar"));
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdate(stack: 'foo')
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		verify(this.stack).create(nullable(String.class), nullable(String.class), anyCollection(),
				anyCollection(), anyCollection(), any(), nullable(String.class), anyString(), nullable(Boolean.class));
	}

	@Test
	void updateExistantStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(true);
		when(this.stack.describeOutputs()).thenReturn(Collections.singletonMap("foo", "bar"));
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdate(stack: 'foo')
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		verify(this.stack).update(nullable(String.class), nullable(String.class), anyCollection(),
				anyCollection(), anyCollection(), any(), nullable(String.class), any());
	}

	@Test
	void doNotCreateNonExistantStack() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(this.stack.exists()).thenReturn(false);
		when(this.stack.describeOutputs()).thenReturn(Collections.singletonMap("foo", "bar"));
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdate(stack: 'foo', create: false)
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		verify(this.stack, never()).create(anyString(), anyString(), anyCollection(), anyCollection(), anyCollection(), any(), anyString(), anyString(), anyBoolean());
	}
}
