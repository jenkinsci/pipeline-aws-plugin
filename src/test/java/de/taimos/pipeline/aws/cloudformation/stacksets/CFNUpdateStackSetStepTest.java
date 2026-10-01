package de.taimos.pipeline.aws.cloudformation.stacksets;

import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackSetResponse;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.StackSet;
import software.amazon.awssdk.services.cloudformation.model.StackInstanceSummary;
import software.amazon.awssdk.services.cloudformation.model.StackSetOperationPreferences;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackSetRequest;
import software.amazon.awssdk.services.cloudformation.model.UpdateStackSetResponse;
import de.taimos.pipeline.aws.AWSClientFactory;
import de.taimos.pipeline.aws.AWSUtilFactory;
import lombok.Builder;
import lombok.Value;
import org.assertj.core.api.Assertions;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;

import java.io.PrintWriter;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WithJenkins
class CFNUpdateStackSetStepTest {

	private JenkinsRule jenkinsRule;
	private CloudFormationStackSet stackSet;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		stackSet = mock(CloudFormationStackSet.class);
		CloudFormationClient cloudFormation = mock(CloudFormationClient.class);
		AWSClientFactory.setFactoryDelegate(x -> cloudFormation);
		AWSUtilFactory.setStackSetSupplier(s -> {
			assertEquals("foo", s);
			return stackSet;
		});
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
		AWSUtilFactory.setStackSetSupplier(null);
	}

	@Test
	void createNonExistantStack() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
		when(stackSet.exists()).thenReturn(false);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdateStackSet(stackSet: 'foo')
                }
                """, true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
		verify(stackSet).create(nullable(String.class), nullable(String.class), anyCollection(), anyCollection(), isNull(String.class), isNull(String.class));
	}

	@Test
	void createNonExistantStackWithCustomAdminArn() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
		when(stackSet.exists()).thenReturn(false);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdateStackSet(stackSet: 'foo', administratorRoleArn: 'bar', executionRoleName: 'baz')
                }
                """, true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		verify(stackSet).create(nullable(String.class), nullable(String.class), anyCollection(), anyCollection(), eq("bar"), eq("baz"));
	}

	@Test
	void updateExistantStack() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(stackSet.exists()).thenReturn(true);
		String operationId = UUID.randomUUID().toString();
		when(stackSet.update(nullable(String.class), nullable(String.class), any(UpdateStackSetRequest.class)))
				.thenReturn(UpdateStackSetResponse.builder().operationId(operationId).build()
				);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdateStackSet(stackSet: 'foo', pollInterval: 27, params: ['foo=bar'], paramsFile: 'params.json')
                }
                """, true)
		);
		try (PrintWriter writer = new PrintWriter(jenkinsRule.jenkins.getWorkspaceFor(job).child("params.json").write())) {
			writer.println("[{\"ParameterKey\": \"foo1\", \"ParameterValue\": \"25\"}]");
		}
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<UpdateStackSetRequest> requestCapture = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		verify(stackSet).update(nullable(String.class), nullable(String.class), requestCapture.capture());
		Assertions.assertThat(requestCapture.getValue().parameters()).containsExactlyInAnyOrder(
				Parameter.builder().parameterKey("foo").parameterValue("bar").build(),
				Parameter.builder().parameterKey("foo1").parameterValue("25").build()
		);

		verify(stackSet).waitForOperationToComplete(operationId, Duration.ofMillis(27));
	}

	@Test
	void updateExistingStackStackSetWithOperationPreferences() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(stackSet.exists()).thenReturn(true);
		String operationId = UUID.randomUUID().toString();
		when(stackSet.update(nullable(String.class), nullable(String.class), any(UpdateStackSetRequest.class)))
				.thenReturn(UpdateStackSetResponse.builder().operationId(operationId).build()
				);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdateStackSet(stackSet: 'foo', operationPreferences: [failureToleranceCount: 5, regionOrder: ['us-west-2'], failureTolerancePercentage: 17, maxConcurrentCount: 18, maxConcurrentPercentage: 34])
                }
                """, true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<UpdateStackSetRequest> requestCapture = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		verify(stackSet).update(nullable(String.class), nullable(String.class), requestCapture.capture());

		Assertions.assertThat(requestCapture.getValue().operationPreferences()).isEqualTo(StackSetOperationPreferences.builder().failureToleranceCount(5).regionOrder("us-west-2").failureTolerancePercentage(17).maxConcurrentCount(18).maxConcurrentPercentage(34).build()
		);

		verify(stackSet).waitForOperationToComplete(operationId, Duration.ofSeconds(1));
	}

	@Test
	void updateExistingStackWithCustomAdminRole() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(stackSet.exists()).thenReturn(true);
		String operationId = UUID.randomUUID().toString();
		when(stackSet.update(nullable(String.class), nullable(String.class), any(UpdateStackSetRequest.class)))
				.thenReturn(UpdateStackSetResponse.builder().operationId(operationId).build()
				);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdateStackSet(stackSet: 'foo', administratorRoleArn: 'bar', executionRoleName: 'baz')\
                }
                """, true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		verify(stackSet).update(nullable(String.class), nullable(String.class), any(UpdateStackSetRequest.class));
	}

	@Test
	void returnsASerializableMapAcrossAStepBoundary() throws Exception {
		// The step used to hand back the raw AWS response. Under v2 that object is not Serializable,
		// so keeping it live across a step boundary - as `def result = ...; echo "..."` does - would
		// fail the build with NotSerializableException. It is converted to a map instead.
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(stackSet.exists()).thenReturn(true);
		when(stackSet.update(nullable(String.class), nullable(String.class), any(UpdateStackSetRequest.class)))
				.thenReturn(UpdateStackSetResponse.builder().operationId(UUID.randomUUID().toString()).build());
		when(stackSet.describe()).thenReturn(DescribeStackSetResponse.builder()
				.stackSet(StackSet.builder().stackSetName("foo").stackSetId("foo:1234").build())
				.build());
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  def result = cfnUpdateStackSet(stackSet: 'foo')
                  echo "stackSetId=${result.stackSet.stackSetId}"
                }
                """, true)
		);

		jenkinsRule.assertLogContains("stackSetId=foo:1234", jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0)));
	}

	@Test
	void doNotCreateNonExistantStack() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(stackSet.exists()).thenReturn(false);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdateStackSet(stackSet: 'foo', create: false)
                }
                """, true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		verify(stackSet, never()).create(anyString(), anyString(), anyCollection(), anyCollection(), isNull(String.class), isNull(String.class));
	}

	@Test
	void updateWithRegionBatches() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "cfnTest");
		when(stackSet.exists()).thenReturn(true);
		String operationId = UUID.randomUUID().toString();
		when(stackSet.update(nullable(String.class), nullable(String.class), any(UpdateStackSetRequest.class)))
				.thenReturn(UpdateStackSetResponse.builder().operationId(operationId).build()
				);
		when(stackSet.findStackSetInstances()).thenReturn(asList(
				StackInstanceSummary.builder().account("a1").region("r1").build(),
				StackInstanceSummary.builder().account("a2").region("r1").build(),
				StackInstanceSummary.builder().account("a2").region("r2").build(),
				StackInstanceSummary.builder().account("a3").region("r3").build()
		));
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  cfnUpdateStackSet(stackSet: 'foo',
                       pollInterval: 27,
                       batchingOptions: [
                         regions: true
                       ]
                    )
                }
                """, true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<UpdateStackSetRequest> requestCapture = ArgumentCaptor.forClass(UpdateStackSetRequest.class);
		verify(stackSet, times(3)).update(nullable(String.class), nullable(String.class), requestCapture.capture());
		Map<String, List<String>> capturedRegionAccounts = requestCapture.getAllValues()
				.stream()
				.flatMap(summary -> summary.regions()
						.stream()
						.flatMap(region -> summary.accounts().stream()
								.map(accountId -> RegionAccountIdTuple.builder().accountId(accountId).region(region).build())
						))
				.collect(Collectors.groupingBy(RegionAccountIdTuple::getRegion, Collectors.mapping(RegionAccountIdTuple::getAccountId, Collectors.toList())));
		Assertions.assertThat(capturedRegionAccounts).containsAllEntriesOf(new HashMap<>() {
            {
                put("r1", asList("a1", "a2"));
                put("r2", singletonList("a2"));
                put("r3", singletonList("a3"));
            }
        });

		verify(stackSet, times(3)).waitForOperationToComplete(any(), any());
	}

	@Value
	@Builder
	private static class RegionAccountIdTuple {
		String region, accountId;
	}
}
