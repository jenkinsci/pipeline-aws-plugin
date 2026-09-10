/*
 * Copyright 2018 CloudBees, Inc.
 *
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
 */

package de.taimos.pipeline.aws;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

@For(S3UploadStep.class)
public class S3UploadStepIntegrationTest {

	@ClassRule
	public static BuildWatcher buildWatcher = new BuildWatcher();

	@Rule
	public JenkinsRule r = new JenkinsRule();

	/**
	 * Runs the upload on a real agent, so it covers the whole remoting path: the callable and its
	 * S3ClientOptions have to serialise, the credentials have to reach the agent, and the AWS client
	 * has to be constructible there. That last part is new under SDK v2 - the transfer manager needs
	 * an asynchronous client, so netty is loaded on the agent rather than only on the controller.
	 *
	 * The upload is expected to fail, and the message is what is asserted: reaching S3 and being told
	 * the bucket name is invalid proves the request was signed on the agent and sent, rather than
	 * failing earlier with a missing class or absent credentials.
	 *
	 * The catch is deliberately broad. Under v1 the AmazonS3Exception itself crossed the channel, so a
	 * pipeline could catch that type; the v2 exception does not survive remoting intact and arrives as
	 * hudson.remoting.ProxyException, so catching an SDK type around an agent-side transfer no longer
	 * works. Recorded in the changelog.
	 */
	@Issue("JENKINS-49025")
	@Test
	public void smokes() throws Exception {
		String globalCredentialsId = "x";
		StandardUsernamePasswordCredentials key = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, globalCredentialsId, "x", "x", "x");
		SystemCredentialsProvider.getInstance().getCredentials().add(key);
		WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
		p.setDefinition(new CpsFlowDefinition(
				"node('" + r.createSlave().getNodeName() + "') {\n" +
						"  withAWS (credentials: '" + globalCredentialsId + "') {\n" +
						"    writeFile file: 'x', text: ''\n" +
						"    try {\n" +
						"      s3Upload bucket: 'x', file: 'x', path: 'x'\n" +
						"      fail 'should not have worked'\n" +
						"    } catch (Exception x) {\n" +
						"      echo(/got $x as expected/)\n" +
						"    }\n" +
						"  }\n" +
						"}\n", true)
		);
		hudson.model.Run<?, ?> run = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
		r.assertLogContains("The specified bucket is not valid", run);
	}

}
