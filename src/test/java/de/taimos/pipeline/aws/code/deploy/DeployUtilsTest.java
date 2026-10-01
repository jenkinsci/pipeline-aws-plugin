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

package de.taimos.pipeline.aws.code.deploy;

import hudson.model.TaskListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import software.amazon.awssdk.services.codedeploy.CodeDeployClient;
import software.amazon.awssdk.services.codedeploy.model.DeploymentInfo;
import software.amazon.awssdk.services.codedeploy.model.DeploymentStatus;
import software.amazon.awssdk.services.codedeploy.model.ErrorInformation;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentRequest;
import software.amazon.awssdk.services.codedeploy.model.GetDeploymentResponse;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * waitDeployment polls until the deployment status matches one of three literals derived from the
 * SDK enum. If those literals stopped matching what the API returns, the loop would never reach a
 * terminal branch and the build would hang rather than fail - which no other test would notice.
 * These steps poll in an unbounded while(true) loop, so a regression in how the status is read
 * would hang the build instead of failing it. The timeout turns that back into a test failure.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class DeployUtilsTest {

	private CodeDeployClient client;
	private TaskListener listener;

	@BeforeEach
	void setup() {
		this.client = mock(CodeDeployClient.class);
		this.listener = mock(TaskListener.class);
		when(this.listener.getLogger()).thenReturn(mock(PrintStream.class));
	}

	/**
	 * The comparison relies on the v2 enum rendering the wire value rather than its Java constant
	 * name. Pinned explicitly because everything else in this class depends on it.
	 */
	@Test
	void statusEnumsRenderTheWireValues() {
		assertThat(DeploymentStatus.SUCCEEDED.toString()).isEqualTo("Succeeded");
		assertThat(DeploymentStatus.FAILED.toString()).isEqualTo("Failed");
		assertThat(DeploymentStatus.STOPPED.toString()).isEqualTo("Stopped");
	}

	private void stubStatus(String status, String errorMessage) {
		DeploymentInfo.Builder info = DeploymentInfo.builder().status(status);
		if (errorMessage != null) {
			info.errorInformation(ErrorInformation.builder().message(errorMessage).build());
		}
		when(this.client.getDeployment(any(GetDeploymentRequest.class)))
				.thenReturn(GetDeploymentResponse.builder().deploymentInfo(info.build()).build());
	}

	@Test
	void returnsOnceTheDeploymentSucceeds() throws Exception {
		this.stubStatus("Succeeded", null);

		new DeployUtils().waitDeployment("d-1", this.listener, this.client);

		verify(this.client).getDeployment(any(GetDeploymentRequest.class));
	}

	@Test
	void failsWithTheErrorMessageFromAws() {
		this.stubStatus("Failed", "the boom happened");

		assertThatThrownBy(() -> new DeployUtils().waitDeployment("d-1", this.listener, this.client))
				.hasMessageContaining("the boom happened");
	}

	@Test
	void failsWhenTheDeploymentIsStopped() {
		this.stubStatus("Stopped", null);

		assertThatThrownBy(() -> new DeployUtils().waitDeployment("d-1", this.listener, this.client))
				.hasMessageContaining("stopped");
	}

	/**
	 * Jenkins aborts a step by interrupting its thread. Swallowing the InterruptedException left
	 * this loop running, so the step kept polling CodeDeploy until the deployment itself reached a
	 * terminal state and the build did not stop when it was aborted.
	 */
	@Test
	void isInterruptibleSoAbortingABuildStopsTheWait() throws Exception {
		this.stubStatus("InProgress", null);
		AtomicReference<Throwable> thrown = new AtomicReference<>();

		Thread worker = new Thread(() -> {
			try {
				new DeployUtils().waitDeployment("d-1", this.listener, this.client);
			} catch (Throwable t) {
				thrown.set(t);
			}
		});
		// daemon so that a regression here - the loop no longer stopping - cannot leave a thread
		// polling the mock for the rest of the surefire fork
		worker.setDaemon(true);
		worker.start();

		// No need to wait for the worker to reach the sleep: the interrupt flag is sticky, and
		// neither the mocked client nor the mocked PrintStream clears it, so Thread.sleep throws
		// as soon as the loop gets there whether or not it has started yet.
		worker.interrupt();
		worker.join(10_000);

		assertThat(worker.isAlive()).isFalse();
		assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
	}

	@Test
	void passesTheDeploymentIdThrough() throws Exception {
		this.stubStatus("Succeeded", null);

		new DeployUtils().waitDeployment("d-42", this.listener, this.client);

		verify(this.client).getDeployment(GetDeploymentRequest.builder().deploymentId("d-42").build());
	}
}
