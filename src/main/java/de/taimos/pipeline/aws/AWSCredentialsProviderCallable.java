/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 Taimos GmbH
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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

import jenkins.MasterToSlaveFileCallable;

import java.io.File;
import java.io.IOException;
import java.io.Serial;

/*
 * Use the FilePath abstraction to execute code on the remote node
 * https://wiki.jenkins.io/display/JENKINS/Making+your+plugin+behave+in+distributed+Jenkins
 *
 */
public class AWSCredentialsProviderCallable extends MasterToSlaveFileCallable<SerializableAWSCredentialsProvider> {

	private final TaskListener listener;

	public AWSCredentialsProviderCallable(TaskListener listener) {
		this.listener = listener;
	}

	@Override
	public SerializableAWSCredentialsProvider invoke(File f, VirtualChannel vc) throws IOException, InterruptedException {
		AWSCredentialsProvider provider = new DefaultAWSCredentialsProviderChain();
		listener.getLogger().println("Retrieving credentials from node.");
		return new SerializableAWSCredentialsProvider(provider);
	}

	@Serial
	private static final long serialVersionUID = 1L;

}
