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

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises snsPublish end to end. SNSPublishStepTest only covers the step's getters, so without
 * this the request the step actually sends to AWS is unasserted.
 */
@WithJenkins
class SNSPublishStepIntegrationTest {

	private JenkinsRule jenkinsRule;
	private SnsClient sns;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.sns = mock(SnsClient.class);
		when(this.sns.publish(any(PublishRequest.class)))
				.thenReturn(PublishResponse.builder().messageId("mid-1").build());
		AWSClientFactory.setFactoryDelegate(x -> this.sns);
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	@Test
	void publishesSubjectAndMessage() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "snsTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  snsPublish(topicArn: 'arn:aws:sns:us-east-1:1:t', subject: 'subj', message: 'msg')
                }
                """, true)
		);

		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
		verify(this.sns).publish(captor.capture());
		assertThat(captor.getValue().topicArn()).isEqualTo("arn:aws:sns:us-east-1:1:t");
		assertThat(captor.getValue().subject()).isEqualTo("subj");
		assertThat(captor.getValue().message()).isEqualTo("msg");
		assertThat(captor.getValue().messageAttributes()).isEmpty();
	}

	@Test
	void messageAttributesAreSentAsStrings() throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, "snsAttrTest");
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  snsPublish(topicArn: 'arn:aws:sns:us-east-1:1:t', subject: 'subj', message: 'msg',
                             messageAttributes: [k1: 'v1', k2: 'v2'])
                }
                """, true)
		);

		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
		verify(this.sns).publish(captor.capture());
		assertThat(captor.getValue().messageAttributes()).hasSize(2);
		assertThat(captor.getValue().messageAttributes().get("k1").stringValue()).isEqualTo("v1");
		assertThat(captor.getValue().messageAttributes().get("k1").dataType()).isEqualTo("String");
		assertThat(captor.getValue().messageAttributes().get("k2").stringValue()).isEqualTo("v2");
	}
}
