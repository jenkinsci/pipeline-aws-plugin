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
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.MultipleFileDownload;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.google.common.base.Preconditions;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public class S3DownloadStep extends AbstractS3Step {

	private final String file;
	private final String bucket;
	private String path = "";
	private boolean force = false;

	@DataBoundConstructor
	public S3DownloadStep(String file, String bucket, boolean pathStyleAccessEnabled, boolean payloadSigningEnabled) {
		super(pathStyleAccessEnabled, payloadSigningEnabled);
		this.file = file;
		this.bucket = bucket;
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

	@DataBoundSetter
	public void setPath(String path) {
		this.path = path;
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

		protected static final long serialVersionUID = 1L;
		@Inject
		protected transient S3DownloadStep step;
		@StepContextParameter
		protected transient EnvVars envVars;
		@StepContextParameter
		protected transient FilePath workspace;
		@StepContextParameter
		protected transient TaskListener listener;

		@Override
		public boolean start() throws Exception {
			final FilePath target = this.workspace.child(this.step.getFile());
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final boolean force = this.step.isForce();

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");

			new Thread("s3Download") {
				@Override
				public void run() {
					try {
						Execution.this.listener.getLogger().format("Downloading s3://%s/%s to %s %n ", bucket, path, target.toURI());
						if (target.exists()) {
							if (force) {
								if (target.isDirectory()) {
									target.deleteRecursive();
								} else {
									target.delete();
								}
							} else {
								Execution.this.listener.getLogger().println("Download failed due to existing target file; set force=true to overwrite target file");
								Execution.this.getContext().onFailure(new RuntimeException("Target exists: " + target.toURI().toString()));
								return;
							}
						}
						target.act(new RemoteDownloader(Execution.this.step.createAmazonS3ClientBuilder(), Execution.this.envVars, Execution.this.listener, bucket, path));
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

	}

	private static class RemoteDownloader implements FilePath.FileCallable<Void> {

		private final transient AmazonS3ClientBuilder amazonS3ClientBuilder;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;

		RemoteDownloader(AmazonS3ClientBuilder amazonS3ClientBuilder, EnvVars envVars, TaskListener taskListener, String bucket, String path) {
			this.amazonS3ClientBuilder = amazonS3ClientBuilder;
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.bucket = bucket;
			this.path = path;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			TransferManager mgr = TransferManagerBuilder.standard()
					.withS3Client(AWSClientFactory.create(amazonS3ClientBuilder, envVars))
					.build();

			if (this.path == null || this.path.isEmpty() || this.path.endsWith("/")) {
				final MultipleFileDownload fileDownload = mgr.downloadDirectory(this.bucket, this.path, localFile);
				fileDownload.waitForCompletion();
				RemoteDownloader.this.taskListener.getLogger().println("Finished: " + fileDownload.getDescription());
				return null;
			} else {
				final Download download = mgr.download(this.bucket, this.path, localFile);
				download.addProgressListener(new ProgressListener() {
					@Override
					public void progressChanged(ProgressEvent progressEvent) {
						if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							RemoteDownloader.this.taskListener.getLogger().println("Finished: " + download.getDescription());
						}
					}
				});
				download.waitForCompletion();
				return null;
			}
		}

		@Override
		public void checkRoles(RoleChecker roleChecker) throws SecurityException {

		}
	}
}
