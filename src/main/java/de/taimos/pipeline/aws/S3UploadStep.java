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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.google.common.base.Preconditions;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

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
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient S3UploadStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final FilePath child = this.workspace.child(this.step.getFile());
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			
			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			Preconditions.checkArgument(path != null && !path.isEmpty(), "Path must not be null or empty");
			
			new Thread("s3Upload") {
				@Override
				public void run() {
					try {
						Execution.this.listener.getLogger().format("Uploading file %s to s3://%s/%s %n", child.toURI(), bucket, path);
						if (!child.exists()) {
							Execution.this.listener.getLogger().println("Upload failed due to missing source file");
							Execution.this.getContext().onFailure(new FileNotFoundException(child.toURI().toString()));
							return;
						}
						
						child.act(new RemoteUploader(Execution.this.envVars, bucket, path));
						
						Execution.this.listener.getLogger().println("Upload complete");
						Execution.this.getContext().onSuccess(null);
					} catch (Exception e) {
						Execution.this.getContext().onFailure(e);
					}
				}
			}.start();
			return false;
		}
		
		@Override
		public void stop(@Nonnull Throwable cause) throws Exception {
			//
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
	private static class RemoteUploader implements FilePath.FileCallable<PutObjectResult> {
		
		private final EnvVars envVars;
		private final String bucket;
		private final String path;
		
		RemoteUploader(EnvVars envVars, String bucket, String path) {
			this.envVars = envVars;
			this.bucket = bucket;
			this.path = path;
		}
		
		@Override
		public PutObjectResult invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			AmazonS3Client s3Client = AWSClientFactory.create(AmazonS3Client.class, envVars);
			return s3Client.putObject(new PutObjectRequest(bucket, path, localFile));
		}
		
		@Override
		public void checkRoles(RoleChecker roleChecker) throws SecurityException {
			
		}
	}
	
}
