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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateSamlProviderRequest;
import software.amazon.awssdk.services.iam.model.CreateSamlProviderResponse;
import software.amazon.awssdk.services.iam.model.ListSamlProvidersResponse;
import software.amazon.awssdk.services.iam.model.SAMLProviderListEntry;
import software.amazon.awssdk.services.iam.model.UpdateSamlProviderRequest;
import software.amazon.awssdk.services.iam.model.UpdateSamlProviderResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compilation proves the v2 class names are right; these tests prove the request fields are
 * populated with the right values and that the create-vs-update branch still turns on whether a
 * provider with a matching name already exists. A swapped builder field would otherwise pass.
 */
public class UpdateIdPStepTests {

	private static final String ARN = "arn:aws:iam::123456789012:saml-provider/myIdP";

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private IamClient iam;

	@Before
	public void setupSdk() throws Exception {
		this.iam = Mockito.mock(IamClient.class);
		AWSClientFactory.setFactoryDelegate((x) -> this.iam);
	}

	@After
	public void tearDownSdk() throws Exception {
		AWSClientFactory.setFactoryDelegate(null);
	}

	private void runStep(String jobName) throws Exception {
		WorkflowJob job = this.jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  writeFile(file: 'metadata.xml', text: '<saml/>')\n"
				+ "  updateIdP(name: 'myIdP', metadata: 'metadata.xml')\n"
				+ "}\n", true)
		);
		this.jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	public void updatesAnExistingProvider() throws Exception {
		Mockito.when(this.iam.listSAMLProviders()).thenReturn(ListSamlProvidersResponse.builder()
				.samlProviderList(SAMLProviderListEntry.builder().arn(ARN).build())
				.build());
		Mockito.when(this.iam.updateSAMLProvider(Mockito.any(UpdateSamlProviderRequest.class)))
				.thenReturn(UpdateSamlProviderResponse.builder().samlProviderArn(ARN).build());

		this.runStep("idpUpdate");

		ArgumentCaptor<UpdateSamlProviderRequest> captor = ArgumentCaptor.forClass(UpdateSamlProviderRequest.class);
		Mockito.verify(this.iam).updateSAMLProvider(captor.capture());
		assertThat(captor.getValue().samlProviderArn()).isEqualTo(ARN);
		assertThat(captor.getValue().samlMetadataDocument()).isEqualTo("<saml/>");
		Mockito.verify(this.iam, Mockito.never()).createSAMLProvider(Mockito.any(CreateSamlProviderRequest.class));
	}

	@Test
	public void createsAProviderWhenNoneMatchesTheName() throws Exception {
		Mockito.when(this.iam.listSAMLProviders()).thenReturn(ListSamlProvidersResponse.builder()
				.samlProviderList(SAMLProviderListEntry.builder()
						.arn("arn:aws:iam::123456789012:saml-provider/someoneElse").build())
				.build());
		Mockito.when(this.iam.createSAMLProvider(Mockito.any(CreateSamlProviderRequest.class)))
				.thenReturn(CreateSamlProviderResponse.builder().samlProviderArn(ARN).build());

		this.runStep("idpCreate");

		ArgumentCaptor<CreateSamlProviderRequest> captor = ArgumentCaptor.forClass(CreateSamlProviderRequest.class);
		Mockito.verify(this.iam).createSAMLProvider(captor.capture());
		assertThat(captor.getValue().name()).isEqualTo("myIdP");
		assertThat(captor.getValue().samlMetadataDocument()).isEqualTo("<saml/>");
		Mockito.verify(this.iam, Mockito.never()).updateSAMLProvider(Mockito.any(UpdateSamlProviderRequest.class));
	}
}
