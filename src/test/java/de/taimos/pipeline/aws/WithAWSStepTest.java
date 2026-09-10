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


import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsImpl;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

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
		AWSClientFactory.setFactoryDelegate(null);
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
		envVars.put(AWSClientFactory.AWS_REGION, "us-west-2");
		envVars.put(AWSClientFactory.AWS_ACCESS_KEY_ID, "AKIAEXAMPLE");
		envVars.put(AWSClientFactory.AWS_SECRET_ACCESS_KEY, "secret");

		// configureV2Builder rather than create: create consults the static factory delegate, which
		// would make this depend on every other test class in the fork having reset it - a leaked
		// delegate would surface here as a ClassCastException on someone else's mock. v2 exposes the
		// override on the built client's configuration rather than on the builder.
		//
		// Closing the client does not release the ApacheHttpClient that configureV2Builder attaches:
		// that is passed as an instance, so the SDK leaves it to the caller. One connection manager
		// per test run is harmless, and the alternative is reaching into the factory's internals.
		try (S3Client client = AWSClientFactory.configureV2Builder(S3Client.builder(), null, envVars).build()) {
			Assert.assertEquals("https://minio.mycompany.com",
					client.serviceClientConfiguration().endpointOverride().map(Object::toString).orElse(null));
		}
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
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithAssumeRoleSAMLAssertion");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (role: 'myRole', roleAccount: '123456789012', principalArn: 'arn:aws:iam::123456789012:saml-provider/test', samlAssertion: 'base64SAML', region: 'eu-west-1') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);
		jenkinsRule.assertBuildStatus(Result.FAILURE, workflowRun);
		jenkinsRule.assertLogContains("Requesting assume role", workflowRun);
		jenkinsRule.assertLogContains("Specified provider doesn't exist", workflowRun);
	}

	@Test
	public void testStepWithAssumeRole() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithAssumeRole");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (role: 'myRole', roleAccount: '123456789012') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);
		jenkinsRule.assertBuildStatus(Result.FAILURE, workflowRun);
		jenkinsRule.assertLogContains("Requesting assume role", workflowRun);
	}

	@Test
	public void testStepWithAssumeRoleChina() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testStepWithAssumeRoleChina");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (role: 'myRole', roleAccount: '123456789012', region: 'cn-north-1') {\n"
				+ "    echo 'It works!'\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);
		jenkinsRule.assertBuildStatus(Result.FAILURE, workflowRun);
		jenkinsRule.assertLogContains("Requesting assume role", workflowRun);
		jenkinsRule.assertLogContains("Assuming role ARN is arn:aws-cn:iam::123456789012:role/myRole" , workflowRun);
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

	/**
	 * A credential implementation whose resolveCredentials actually returns, so the session-token
	 * branch is reachable. The existing folder-credential tests all use an AWSCredentialsImpl with a
	 * placeholder role ARN, so resolveCredentials throws inside aws-credentials before returning -
	 * which is what those tests assert on, and why nothing reached this code.
	 */
	public static class StubCredentials extends BaseStandardCredentials implements AmazonWebServicesCredentials {

		private static final long serialVersionUID = 1L;
		// stored as strings, not as an AwsCredentials: the credentials store persists this object and
		// the SDK's credential types are not serializable
		private final String accessKeyId;
		private final String secretAccessKey;
		private final String sessionToken;

		StubCredentials(String id, String accessKeyId, String secretAccessKey, String sessionToken) {
			super(CredentialsScope.GLOBAL, id, "stub");
			this.accessKeyId = accessKeyId;
			this.secretAccessKey = secretAccessKey;
			this.sessionToken = sessionToken;
		}

		@Override
		public AwsCredentials resolveCredentials() {
			if ("third-party".equals(this.sessionToken)) {
				return new ThirdPartySessionCredentials();
			}
			return this.sessionToken == null
					? AwsBasicCredentials.create(this.accessKeyId, this.secretAccessKey)
					: AwsSessionCredentials.create(this.accessKeyId, this.secretAccessKey, this.sessionToken);
		}

		@Override
		public AwsCredentials resolveCredentials(String mfaToken) {
			return this.resolveCredentials();
		}

		// AmazonWebServicesCredentials still extends the v1 provider interface, so these two have to
		// exist even though nothing in the plugin calls them any more. They go when aws-credentials
		// drops SDK v1.
		@Override
		public com.amazonaws.auth.AWSCredentials getCredentials() {
			throw new UnsupportedOperationException("v1 path not used");
		}

		@Override
		public com.amazonaws.auth.AWSCredentials getCredentials(String mfaToken) {
			throw new UnsupportedOperationException("v1 path not used");
		}

		@Override
		public void refresh() {
		}

		@Override
		public String getDisplayName() {
			return "stub";
		}
	}

	private void registerStub(String id, String sessionToken) throws Exception {
		SystemCredentialsProvider.getInstance().getCredentials().add(new StubCredentials(id, "key", "secret", sessionToken));
		SystemCredentialsProvider.getInstance().save();
	}

	/**
	 * A session credential that is not the SDK's own AwsSessionCredentials. AmazonWebServicesCredentials
	 * is an extension point, so an implementation outside aws-credentials can return its own type;
	 * without this, narrowing the check back to the concrete class would leave the suite green while
	 * silently dropping such a token.
	 */
	public static class ThirdPartySessionCredentials implements AwsCredentials, AwsSessionCredentialsIdentity {
		@Override
		public String accessKeyId() {
			return "key";
		}

		@Override
		public String secretAccessKey() {
			return "secret";
		}

		@Override
		public String sessionToken() {
			return "third-party-token";
		}
	}

	private WorkflowRun runEchoingSessionToken(String jobName, String credentialsId) throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, jobName);
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withAWS (credentials: '" + credentialsId + "') {\n"
				+ "    echo \"token=[${env.AWS_SESSION_TOKEN}]\"\n"
				+ "  }\n"
				+ "}\n", true)
		);
		return jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));
	}

	/**
	 * v1 read the session token only on the MFA branch, so a session credential resolved without one
	 * lost its token and left a key/secret pair that cannot sign.
	 */
	@Test
	public void sessionCredentialsExportTheirTokenWithoutMfa() throws Exception {
		this.registerStub("stub-session-creds", "the-session-token");

		WorkflowRun run = this.runEchoingSessionToken("testSessionToken", "stub-session-creds");

		jenkinsRule.assertLogContains("token=[the-session-token]", run);
	}

	/**
	 * Nesting static keys inside an assumed role must not leave the outer block's token in place: the
	 * inner block would otherwise sign with the new key and secret and the old token, which AWS
	 * rejects.
	 */
	@Test
	public void basicCredentialsClearAnInheritedSessionToken() throws Exception {
		this.registerStub("stub-basic-creds-nested", null);

		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testClearsInheritedToken");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withEnv(['AWS_SESSION_TOKEN=inherited-token']) {\n"
				+ "    withAWS (credentials: 'stub-basic-creds-nested') {\n"
				+ "      echo \"token=[${env.AWS_SESSION_TOKEN}]\"\n"
				+ "    }\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkinsRule.assertLogContains("token=[null]", run);
	}

	@Test
	public void basicCredentialsExportNoToken() throws Exception {
		this.registerStub("stub-basic-creds", null);

		WorkflowRun run = this.runEchoingSessionToken("testNoSessionToken", "stub-basic-creds");

		jenkinsRule.assertLogContains("token=[null]", run);
	}


	/**
	 * The reason the check is against AwsSessionCredentialsIdentity rather than the SDK's own
	 * AwsSessionCredentials: a third-party AmazonWebServicesCredentials returning its own session
	 * credential type must still have its token exported.
	 */
	@Test
	public void aThirdPartySessionCredentialAlsoExportsItsToken() throws Exception {
		this.registerStub("stub-third-party-creds", "third-party");

		WorkflowRun run = this.runEchoingSessionToken("testThirdPartyToken", "stub-third-party-creds");

		jenkinsRule.assertLogContains("token=[third-party-token]", run);
	}

	/**
	 * The branch the changelog's "static keys" example actually points at: README documents a Jenkins
	 * username/password credential as the way to pass an access key and secret, and withCredentials
	 * tests that branch first. It installs a key and secret without touching the token, so an inherited
	 * one has to be dropped here too.
	 */
	@Test
	public void usernamePasswordCredentialsClearAnInheritedSessionToken() throws Exception {
		String credentialsId = "user-pass-creds-nested";
		StandardUsernamePasswordCredentials credentials = new UsernamePasswordCredentialsImpl(
				CredentialsScope.GLOBAL, credentialsId, "desc", "access-key-id", "secret-access-key");
		SystemCredentialsProvider.getInstance().getCredentials().add(credentials);
		SystemCredentialsProvider.getInstance().save();

		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testUserPassClearsToken");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withEnv(['AWS_SESSION_TOKEN=inherited-token']) {\n"
				+ "    withAWS (credentials: '" + credentialsId + "') {\n"
				+ "      echo \"token=[${env.AWS_SESSION_TOKEN}]\"\n"
				+ "      echo \"key=[${env.AWS_ACCESS_KEY_ID}]\"\n"
				+ "    }\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun run = jenkinsRule.assertBuildStatusSuccess(job.scheduleBuild2(0));

		jenkinsRule.assertLogContains("token=[null]", run);
		jenkinsRule.assertLogContains("key=[access-key-id]", run);
		// the drop is logged, so the 403s it can cause are diagnosable
		jenkinsRule.assertLogContains("Dropping the inherited AWS_SESSION_TOKEN", run);
	}

	/**
	 * The SAML branch in the shape it is actually used - samlAssertion on its own does nothing, since
	 * only withRole consumes it.
	 *
	 * This pins that the documented flow still reaches STS with an inherited token present; it does
	 * not demonstrate that the clear is what makes that work. Checked by removing the clear: the error
	 * is unchanged, so the inherited token does not affect the AssumeRoleWithSAML call. The clear
	 * still matters on this branch for anything in the body that would otherwise inherit a stale
	 * token alongside the placeholder keys.
	 */
	@Test
	public void samlAssertionStillReachesStsWithAnInheritedToken() throws Exception {
		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testSamlWithInheritedToken");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withEnv(['AWS_SESSION_TOKEN=inherited-token']) {\n"
				+ "    withAWS (role: 'myRole', roleAccount: '123456789012', principalArn: 'arn:aws:iam::123456789012:saml-provider/test', samlAssertion: 'base64SAML', region: 'eu-west-1') {\n"
				+ "      echo 'It works!'\n"
				+ "    }\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);
		jenkinsRule.assertBuildStatus(Result.FAILURE, workflowRun);

		// anchor: shows the run got past withCredentials with the inherited token installed,
		// so the STS error below cannot have come from a failure earlier in the step
		jenkinsRule.assertLogContains("Requesting assume role", workflowRun);
		jenkinsRule.assertLogContains("Specified provider doesn't exist", workflowRun);
	}

	/**
	 * The drop is not announced when a later stage installs a token of its own - start() runs withRole
	 * after withCredentials - or the message would be a false alarm on a build that ends up fine.
	 */
	@Test
	public void nothingIsLoggedWhenARoleWillSupplyAToken() throws Exception {
		String credentialsId = "user-pass-creds-with-role";
		SystemCredentialsProvider.getInstance().getCredentials().add(new UsernamePasswordCredentialsImpl(
				CredentialsScope.GLOBAL, credentialsId, "desc", "access-key-id", "secret-access-key"));
		SystemCredentialsProvider.getInstance().save();

		WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "testQuietWhenRoleFollows");
		job.setDefinition(new CpsFlowDefinition(""
				+ "node {\n"
				+ "  withEnv(['AWS_SESSION_TOKEN=inherited-token']) {\n"
				+ "    withAWS (credentials: '" + credentialsId + "', role: 'myRole', roleAccount: '123456789012') {\n"
				+ "      echo 'It works!'\n"
				+ "    }\n"
				+ "  }\n"
				+ "}\n", true)
		);
		WorkflowRun workflowRun = job.scheduleBuild2(0).get();
		jenkinsRule.waitForCompletion(workflowRun);

		// anchor first: without it the negative assertion also passes when the run never got as far as
		// withCredentials - a mistyped credentials id would leave this green for the wrong reason
		jenkinsRule.assertLogContains("Requesting assume role", workflowRun);
		jenkinsRule.assertLogNotContains("Dropping the inherited AWS_SESSION_TOKEN", workflowRun);
	}

	/**
	 * No inherited token means nothing to report - the log line exists to explain a drop, not to
	 * appear on every withAWS.
	 */
	@Test
	public void nothingIsLoggedWhenThereIsNoTokenToDrop() throws Exception {
		this.registerStub("stub-basic-creds-quiet", null);

		WorkflowRun run = this.runEchoingSessionToken("testQuietWhenNoToken", "stub-basic-creds-quiet");

		jenkinsRule.assertLogNotContains("Dropping the inherited AWS_SESSION_TOKEN", run);
	}
}
