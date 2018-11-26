package de.taimos.pipeline.aws;

/*-
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 - 2017 Taimos GmbH
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


import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.Result;

import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.EnvVars;
import hudson.util.ListBoxModel;

/**
 * Test the behavior of the {@link WithAWSStep}
 *
 * @author Allan Burdajewicz
 */
public class WithAWSStepTest {

	@ClassRule
	public static JenkinsRule jenkinsRule = new JenkinsRule();

	@Before
	public void before() throws Exception {
		List<Credentials> credentials = SystemCredentialsProvider.getInstance().getCredentials();
		SystemCredentialsProvider.getInstance().getCredentials().removeAll(credentials);
		SystemCredentialsProvider.getInstance().save();
	}

	@Test
	public void testStepWithGlobalCredentials() throws Exception {

		String globalCredentialsId = "global-aws-creds";

		List<String> credentialIds = new ArrayList<>();
		credentialIds.add(globalCredentialsId);

		StandardUsernamePasswordCredentials key = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
				globalCredentialsId, "test-global-creds", "global-aws-access-key-id", "global-aws-secret-access-key");
		SystemCredentialsProvider.getInstance().getCredentials().add(key);
		SystemCredentialsProvider.getInstance().save();

		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalCredentials");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (credentials: '" + globalCredentialsId + "') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	public void testStepWithBasicAndAwsGlobalCredentials() throws Exception {

		String globalBaseCreds = "global-basic-creds";
		String globalAwsCreds = "global-aws-creds";

		List<String> credentialIds = new ArrayList<>();
		credentialIds.add(globalBaseCreds);

		StandardUsernamePasswordCredentials key = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
				globalBaseCreds, "test-global-creds", "global-aws-access-key-id", "global-aws-secret-access-key");

		AmazonWebServicesCredentials amazonWebServicesCredentials = new AWSCredentialsImpl(CredentialsScope.GLOBAL,
				globalAwsCreds, "global-aws-access-key-id", "global-aws-secret-access-key", "Aws-Description",
				"Arn::Something:or:Other", "12345678");

		SystemCredentialsProvider.getInstance().getCredentials().add(amazonWebServicesCredentials);
		SystemCredentialsProvider.getInstance().getCredentials().add(key);
		SystemCredentialsProvider.getInstance().save();

		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithBasicAndAwsGlobalCredentials");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (credentials: '" + globalBaseCreds + "') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	@Test
	public void testStepWithNotFoundGlobalCredentials() throws Exception {

		String globalBaseCreds = "something-random";

		List<String> credentialIds = new ArrayList<>();
		credentialIds.add(globalBaseCreds);

		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithNotFoundGlobalCredentials");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (credentials: '" + globalBaseCreds + "') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);

		jenkinsRule.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
	}

	@Test
	public void testStepWithGlobalAWSCredentials() throws Exception {

		String globalCredentialsId = "global-aws-creds";

		List<String> credentialIds = new ArrayList<>();
		credentialIds.add(globalCredentialsId);

		AmazonWebServicesCredentials amazonWebServicesCredentials = new AWSCredentialsImpl(CredentialsScope.GLOBAL,
				globalCredentialsId, "global-aws-access-key-id", "global-aws-secret-access-key", "Aws-Description",
				"Arn::Something:or:Other", "12345678");

		SystemCredentialsProvider.getInstance().getCredentials().add(amazonWebServicesCredentials);
		SystemCredentialsProvider.getInstance().save();

		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithGlobalAWSCredentials");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (credentials: '" + globalCredentialsId + "') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);


		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);
		jenkinsRule.assertBuildStatus(Result.FAILURE, workflowRun);
		jenkinsRule.assertLogContains("The security token included in the request is invalid.", workflowRun);
	}

	@Test
	public void testSettingEndpointUrl() throws Exception {
		final EnvVars envVars = new EnvVars();
		envVars.put(AWSClientFactory.AWS_ENDPOINT_URL, "https://minio.mycompany.com");
		envVars.put(AWSClientFactory.AWS_REGION, Regions.DEFAULT_REGION.getName());
		final AmazonS3ClientBuilder amazonS3ClientBuilder = AWSClientFactory.configureBuilder(AmazonS3ClientBuilder.standard(), envVars);
		Assert.assertEquals("https://minio.mycompany.com", amazonS3ClientBuilder.getEndpoint().getServiceEndpoint());

	}

	@Test
	public void testStepWithFolderCredentials() throws Exception {

		String folderCredentialsId = "folders-aws-creds";

		// Create a folder with credentials in its store
		Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder" + jenkinsRule.jenkins.getItems().size());
		CredentialsStore folderStore = this.getFolderStore(folder);
		StandardUsernamePasswordCredentials inFolderCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
																									  folderCredentialsId, "test-folder-creds", "folder-aws-access-key-id", "folder-aws-secret-access-key");
		folderStore.addCredentials(Domain.global(), inFolderCredentials);
		SystemCredentialsProvider.getInstance().save();

		List<String> credentialIds = new ArrayList<>();
		credentialIds.add(folderCredentialsId);

		WorkflowJob job = folder.createProject(WorkflowJob.class, "testStepWithFolderCredentials");
		job.setDefinition(new CpsFlowDefinition(""
														+ "node {\n"
														+ "  withAWS (credentials: '" + folderCredentialsId + "') {\n"
														+ "    echo 'It works!'\n"
														+ "  }\n"
														+ "}\n", true)
		);
		jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

	}

	@Test
	public void testStepWithAWSFolderCredentials() throws Exception {

		String folderCredentialsId = "folders-aws-creds";

		// Create a folder with credentials in its store
		Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder" + jenkinsRule.jenkins.getItems().size());
		CredentialsStore folderStore = this.getFolderStore(folder);
		AmazonWebServicesCredentials amazonWebServicesCredentials = new AWSCredentialsImpl(CredentialsScope.GLOBAL,
				folderCredentialsId, "global-aws-access-key-id", "global-aws-secret-access-key", "Aws-Description",
				"Arn::Something:or:Other", "12345678");
		folderStore.addCredentials(Domain.global(), amazonWebServicesCredentials);
		SystemCredentialsProvider.getInstance().save();

		List<String> credentialIds = new ArrayList<>();
		credentialIds.add(folderCredentialsId);

		WorkflowJob job = folder.createProject(WorkflowJob.class, "testStepWithAWSFolderCredentials");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (credentials: '" + folderCredentialsId + "') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);
		jenkinsRule.assertBuildStatus(Result.FAILURE, workflowRun);
		jenkinsRule.assertLogContains("The security token included in the request is invalid.", workflowRun);
		jenkinsRule.assertLogContains("Constructing AWS Credentials", workflowRun);

	}

	@Test
	public void testStepWithAWSIamMFAFolderCredentials() throws Exception {

		String folderCredentialsId = "folders-aws-creds";

		// Create a folder with credentials in its store
		Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder" + jenkinsRule.jenkins.getItems().size());
		CredentialsStore folderStore = this.getFolderStore(folder);
		AmazonWebServicesCredentials amazonWebServicesCredentials = new AWSCredentialsImpl(CredentialsScope.GLOBAL,
				folderCredentialsId, "global-aws-access-key-id", "global-aws-secret-access-key", "Aws-Description",
				"Arn::Something:or:Other", "12345678");
		folderStore.addCredentials(Domain.global(), amazonWebServicesCredentials);
		SystemCredentialsProvider.getInstance().save();

		List<String> credentialIds = new ArrayList<>();
		credentialIds.add(folderCredentialsId);

		WorkflowJob job = folder.createProject(WorkflowJob.class, "testStepWithAWSIamMFAFolderCredentials");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (credentials: '" + folderCredentialsId + "', iamMfaToken: '1234567') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);
		jenkinsRule.assertBuildStatus(Result.FAILURE, workflowRun);
		jenkinsRule.assertLogContains("The security token included in the request is invalid.", workflowRun);
		jenkinsRule.assertLogContains("Constructing AWS Credentials", workflowRun);
		jenkinsRule.assertLogContains("utilizing MFA Token", workflowRun);

	}

	@Test
	public void testStepWithAssumeRoleSAMLAssertion() throws Exception {

		// Create a folder with credentials in its store
		Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder" + jenkinsRule.jenkins.getItems().size());

		WorkflowJob job = folder.createProject(WorkflowJob.class, "testStepWithAWSIamMFAFolderCredentials");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (role: 'myRole', roleAccount: '123456789', principalArn: 'arn:aws:iam::123456789:saml-provider/test', samlAssertion: 'base64SAML', region: 'eu-west-1') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);
		jenkinsRule.assertBuildStatus(Result.FAILURE, workflowRun);
		jenkinsRule.assertLogContains("Requesting assume role", workflowRun);
		jenkinsRule.assertLogContains("Invalid base64 SAMLResponse", workflowRun);
	}

	@Test
	public void testListCredentials() throws Exception {
		Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder" + jenkinsRule.jenkins.getItems().size());
		CredentialsStore folderStore = this.getFolderStore(folder);
		StandardUsernamePasswordCredentials folderCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
				"folder-creds", "test-creds", "aws-access-key-id", "aws-secret-access-key");
		StandardUsernamePasswordCredentials globalCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
				"global-creds", "test-creds", "aws-access-key-id", "aws-secret-access-key");

		folderStore.addCredentials(Domain.global(), folderCredentials);
		SystemCredentialsProvider.getInstance().getCredentials().add(globalCredentials);
		SystemCredentialsProvider.getInstance().save();

		WorkflowJob job = folder.createProject(WorkflowJob.class, "testStepWithFolderCredentials");
		final WithAWSStep.DescriptorImpl descriptor = jenkinsRule.jenkins.getDescriptorByType(WithAWSStep.DescriptorImpl.class);

		// 3 options: Root credentials, folder credentials and "none"
		ListBoxModel list = descriptor.doFillCredentialsItems(job);
		Assert.assertEquals(3, list.size());

		StandardUsernamePasswordCredentials systemCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM,
				"system-creds", "test-creds", "aws-access-key-id", "aws-secret-access-key");
		SystemCredentialsProvider.getInstance().getCredentials().add(systemCredentials);

		// Still 3 options: Root credentials, folder credentials and "none"
		list = descriptor.doFillCredentialsItems(job);
		Assert.assertEquals(3, list.size());
	}

	@Test
	public void testListAWSCredentials() throws Exception {

		Folder folder = jenkinsRule.jenkins.createProject(Folder.class, "folder" + jenkinsRule.jenkins.getItems().size());
		CredentialsStore folderStore = this.getFolderStore(folder);
		AmazonWebServicesCredentials amazonWebServicesCredentials = new AWSCredentialsImpl(CredentialsScope.GLOBAL,
				"test-aws-creds", "global-aws-access-key-id", "global-aws-secret-access-key", "Aws-Description",
				"Arn::Something:or:Other", "12345678");
		AmazonWebServicesCredentials globalAmazonWebServicesCredentials = new AWSCredentialsImpl(CredentialsScope.GLOBAL,
				"global-test-aws-creds", "global-aws-access-key-id", "global-aws-secret-access-key", "Aws-Description",
				"Arn::Something:or:Other", "12345678");

		folderStore.addCredentials(Domain.global(), amazonWebServicesCredentials);
		SystemCredentialsProvider.getInstance().getCredentials().add(globalAmazonWebServicesCredentials);
		SystemCredentialsProvider.getInstance().save();

		WorkflowJob job = folder.createProject(WorkflowJob.class, "testStepWithFolderCredentials");
		final WithAWSStep.DescriptorImpl descriptor = jenkinsRule.jenkins.getDescriptorByType(WithAWSStep.DescriptorImpl.class);

		// 3 options: Root credentials, folder credentials and "none"
		ListBoxModel list = descriptor.doFillCredentialsItems(job);
		Assert.assertEquals(3, list.size());

		StandardUsernamePasswordCredentials systemCredentials = new UsernamePasswordCredentialsImpl(CredentialsScope.SYSTEM,
				"system-creds", "test-creds", "aws-access-key-id", "aws-secret-access-key");
		SystemCredentialsProvider.getInstance().getCredentials().add(systemCredentials);

		// Still 3 options: Root credentials, folder credentials and "none"
		list = descriptor.doFillCredentialsItems(job);
		Assert.assertEquals(3, list.size());
	}

	private CredentialsStore getFolderStore(AbstractFolder f) {
		Iterable<CredentialsStore> stores = CredentialsProvider.lookupStores(f);
		CredentialsStore folderStore = null;
		for (CredentialsStore s : stores) {
			if (s.getProvider() instanceof FolderCredentialsProvider && s.getContext() == f) {
				folderStore = s;
				break;
			}
		}
		return folderStore;
	}
}
