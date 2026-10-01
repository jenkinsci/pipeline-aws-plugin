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
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateSamlProviderRequest;
import software.amazon.awssdk.services.iam.model.CreateSamlProviderResponse;
import software.amazon.awssdk.services.iam.model.ListSamlProvidersResponse;
import software.amazon.awssdk.services.iam.model.SAMLProviderListEntry;
import software.amazon.awssdk.services.iam.model.UpdateSamlProviderRequest;
import software.amazon.awssdk.services.iam.model.UpdateSamlProviderResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Compilation proves the v2 class names are right; these tests prove the request fields are
 * populated with the right values and that the create-vs-update branch still turns on whether a
 * provider with a matching name already exists. A swapped builder field would otherwise pass.
 */
@WithJenkins
class UpdateIdPStepTests {

	private static final String ARN = "arn:aws:iam::123456789012:saml-provider/myIdP";

	private JenkinsRule jenkinsRule;
	private IamClient iam;

	@BeforeEach
	void setupSdk(JenkinsRule rule) {
		this.jenkinsRule = rule;
		this.iam = mock(IamClient.class);
		AWSClientFactory.setFactoryDelegate(x -> this.iam);
	}

	@AfterEach
	void tearDownSdk() {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void runStep(String jobName) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile(file: 'metadata.xml', text: '<saml/>')
                  updateIdP(name: 'myIdP', metadata: 'metadata.xml')
                }
                """, true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	void updatesAnExistingProvider() throws Exception {
		when(this.iam.listSAMLProviders()).thenReturn(ListSamlProvidersResponse.builder()
				.samlProviderList(SAMLProviderListEntry.builder().arn(ARN).build())
				.build());
		when(this.iam.updateSAMLProvider(any(UpdateSamlProviderRequest.class)))
				.thenReturn(UpdateSamlProviderResponse.builder().samlProviderArn(ARN).build());

		this.runStep("idpUpdate");

		ArgumentCaptor<UpdateSamlProviderRequest> captor = ArgumentCaptor.forClass(UpdateSamlProviderRequest.class);
		verify(this.iam).updateSAMLProvider(captor.capture());
		assertThat(captor.getValue().samlProviderArn()).isEqualTo(ARN);
		assertThat(captor.getValue().samlMetadataDocument()).isEqualTo("<saml/>");
		verify(this.iam, never()).createSAMLProvider(any(CreateSamlProviderRequest.class));
	}

	@Test
	void createsAProviderWhenNoneMatchesTheName() throws Exception {
		when(this.iam.listSAMLProviders()).thenReturn(ListSamlProvidersResponse.builder()
				.samlProviderList(SAMLProviderListEntry.builder()
						.arn("arn:aws:iam::123456789012:saml-provider/someoneElse").build())
				.build());
		when(this.iam.createSAMLProvider(any(CreateSamlProviderRequest.class)))
				.thenReturn(CreateSamlProviderResponse.builder().samlProviderArn(ARN).build());

		this.runStep("idpCreate");

		ArgumentCaptor<CreateSamlProviderRequest> captor = ArgumentCaptor.forClass(CreateSamlProviderRequest.class);
		verify(this.iam).createSAMLProvider(captor.capture());
		assertThat(captor.getValue().name()).isEqualTo("myIdP");
		assertThat(captor.getValue().samlMetadataDocument()).isEqualTo("<saml/>");
		verify(this.iam, never()).updateSAMLProvider(any(UpdateSamlProviderRequest.class));
	}
}
