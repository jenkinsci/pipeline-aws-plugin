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
import java.io.FileOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.base.Preconditions;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public class S3DownloadStep extends AbstractStepImpl {
	
	private final String file;
	private final String bucket;
	private final String path;
	private boolean force = false;
	
	@DataBoundConstructor
	public S3DownloadStep(String file, String bucket, String path) {
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
	
	public boolean isForce() {
		return this.force;
	}
	
	@DataBoundSetter
	public void setForce(boolean force) {
		this.force = force;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "s3Download";
		}
		
		@Override
		public String getDisplayName() {
			return "Copy file from S3";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient S3DownloadStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final FilePath target = this.workspace.child(this.step.getFile());
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final boolean force = this.step.isForce();
			
			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			Preconditions.checkArgument(path != null && !path.isEmpty(), "Path must not be null or empty");
			
			new Thread("s3Download") {
				@Override
				public void run() {
					try {
						Execution.this.listener.getLogger().format("Downloading file s3://%s/%s to %s %n ", bucket, path, target.toURI());
						if (target.exists()) {
							if (force) {
								target.delete();
							} else {
								Execution.this.listener.getLogger().println("Download failed due to existing target file; set force=true to overwrite target file");
								Execution.this.getContext().onFailure(new RuntimeException("Target exists: " + target.toURI().toString()));
								return;
							}
						}
						target.act(new RemoteDownloader(Execution.this.envVars, bucket, path));
						Execution.this.listener.getLogger().println("Download complete");
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
	
	private static class RemoteDownloader implements FilePath.FileCallable<S3Object> {
		
		private final EnvVars envVars;
		private final String bucket;
		private final String path;
		
		RemoteDownloader(EnvVars envVars, String bucket, String path) {
			this.envVars = envVars;
			this.bucket = bucket;
			this.path = path;
		}
		
		@Override
		public S3Object invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			AmazonS3Client s3Client = AWSClientFactory.create(AmazonS3Client.class, envVars);
			S3Object object = s3Client.getObject(new GetObjectRequest(bucket, path));
			IOUtils.copy(object.getObjectContent(), new FileOutputStream(localFile));
			return object;
		}
		
		@Override
		public void checkRoles(RoleChecker roleChecker) throws SecurityException {
			
		}
	}
}
