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

import java.io.FileNotFoundException;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class S3UploadStep extends AbstractStepImpl {
	
	private final String file;
	private final String bucket;
	private final String path;
	
	@DataBoundConstructor
	public S3UploadStep(String file, String bucket, String path) {
		this.file = file;
		this.bucket = bucket;
		this.path = path;
	}
	
	public String getFile() {
		return this.file;
	}
	
	public String getBucket() {
		return this.bucket;
	}
	
	public String getPath() {
		return this.path;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "s3Upload";
		}
		
		@Override
		public String getDisplayName() {
			return "Copy file to S3";
		}
	}
	
	public static class Execution extends AbstractSynchronousStepExecution<Void> {
		
		@Inject
		private transient S3UploadStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		protected Void run() throws Exception {
			FilePath child = this.workspace.child(this.step.getFile());
			String bucket = this.step.getBucket();
			String path = this.step.getPath();
			
			AmazonS3Client s3Client = AWSClientFactory.create(AmazonS3Client.class, this.envVars);
			
			this.listener.getLogger().format("Uploading file %s to s3://%s/%s %n", child.toURI(), bucket, path);
			if (!child.exists()) {
				this.listener.getLogger().println("Upload failed due to missing source file");
				throw new FileNotFoundException(child.toURI().toString());
			}
			s3Client.putObject(bucket, path, child.read(), new ObjectMetadata());
			this.listener.getLogger().println("Upload complete");
			return null;
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
