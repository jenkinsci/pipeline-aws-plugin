/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2026 Taimos GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package de.taimos.pipeline.aws;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LogType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InvokeLambdaStepTest only covers turning the payload parameter into a string. The request the
 * step sends and what it does with the response - both rewritten for the v2 SdkBytes payload -
 * were unasserted.
 */
public class InvokeLambdaStepIntegrationTest {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private LambdaClient lambda;

	@Before
	public void setupSdk() throws Exception {
		this.lambda = Mockito.mock(LambdaClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.lambda);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void stubInvoke(String payload, String functionError) {
		InvokeResponse.Builder response = InvokeResponse.builder()
				.payload(SdkBytes.fromString(payload, StandardCharsets.UTF_8))
				.logResult(Base64.getEncoder().encodeToString("some log output".getBytes(StandardCharsets.UTF_8)));
		if (functionError != null) {
			response.functionError(functionError);
		}
		Mockito.when(this.lambda.invoke(Mockito.any(InvokeRequest.class))).thenReturn(response.build());
	}

	private WorkflowRun run(String jobName, String script, Result expected) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition("node {\n" + script + "\n}\n", true));
		return this.jenkinsRule.assertBuildStatus(expected, job.scheduleBuild2(0));
	}

	@Test
	public void sendsTheFunctionNamePayloadAndLogType() throws Exception {
		this.stubInvoke("{\"ok\":true}", null);

		this.run("lambdaInvoke", "  invokeLambda(functionName: 'fn', payload: [key: 'value'])", Result.SUCCESS);

		ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
		Mockito.verify(this.lambda).invoke(captor.capture());
		assertThat(captor.getValue().functionName()).isEqualTo("fn");
		assertThat(captor.getValue().payload().asString(StandardCharsets.UTF_8)).isEqualTo("{\"key\":\"value\"}");
		assertThat(captor.getValue().logType()).isEqualTo(LogType.TAIL);
	}

	/**
	 * Invoke accepts a request with no payload and both payload parameters are optional, so
	 * invokeLambda(functionName: 'fn') on its own is a legal call.
	 */
	@Test
	public void worksWithoutAPayload() throws Exception {
		this.stubInvoke("{}", null);

		this.run("lambdaNoPayload", "  invokeLambda(functionName: 'fn')", Result.SUCCESS);

		ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
		Mockito.verify(this.lambda).invoke(captor.capture());
		assertThat(captor.getValue().payload()).isNull();
	}

	/**
	 * payloadAsString is the other documented way to supply input, and it takes a different route
	 * into the SdkBytes conversion than the payload parameter.
	 */
	@Test
	public void sendsAStringPayloadUnchanged() throws Exception {
		this.stubInvoke("{}", null);

		this.run("lambdaStringPayload",
				"  invokeLambda(functionName: 'fn', payloadAsString: '{\"key\": \"value\"}')",
				Result.SUCCESS);

		ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.forClass(InvokeRequest.class);
		Mockito.verify(this.lambda).invoke(captor.capture());
		assertThat(captor.getValue().payload().asString(StandardCharsets.UTF_8)).isEqualTo("{\"key\": \"value\"}");
	}

	@Test
	public void parsesTheResponsePayloadByDefault() throws Exception {
		this.stubInvoke("{\"answer\":42}", null);

		WorkflowRun run = this.run("lambdaParsed",
				"  def out = invokeLambda(functionName: 'fn', payload: [:])\n  echo \"answer=${out.answer}\"",
				Result.SUCCESS);

		this.jenkinsRule.assertLogContains("answer=42", run);
	}

	@Test
	public void returnsTheRawStringWhenAsked() throws Exception {
		this.stubInvoke("{\"answer\":42}", null);

		WorkflowRun run = this.run("lambdaRaw",
				"  def out = invokeLambda(functionName: 'fn', payload: [:], returnValueAsString: true)\n  echo \"raw=${out}\"",
				Result.SUCCESS);

		this.jenkinsRule.assertLogContains("raw={\"answer\":42}", run);
	}

	/**
	 * A Lambda that fails returns 200 with functionError set, so the step has to inspect the
	 * response rather than rely on an exception.
	 */
	@Test
	public void failsTheBuildWhenTheFunctionReportsAnError() throws Exception {
		this.stubInvoke("{\"errorMessage\":\"boom\"}", "Unhandled");

		WorkflowRun run = this.run("lambdaError", "  invokeLambda(functionName: 'fn', payload: [:])", Result.FAILURE);

		this.jenkinsRule.assertLogContains("boom", run);
	}

	@Test
	public void logsTheDecodedTailOutput() throws Exception {
		this.stubInvoke("{}", null);

		WorkflowRun run = this.run("lambdaLog", "  invokeLambda(functionName: 'fn', payload: [:])", Result.SUCCESS);

		this.jenkinsRule.assertLogContains("some log output", run);
	}
}
