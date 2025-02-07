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

import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.MultipleFileDownload;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.util.Set;

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

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new S3DownloadStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
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

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

		@Serial
		private static final long serialVersionUID = 1L;

		protected transient S3DownloadStep step;

		public Execution(S3DownloadStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public Void run() throws Exception {
			final FilePath target = this.getContext().get(FilePath.class).child(this.step.getFile());
			final TaskListener listener = this.getContext().get(TaskListener.class);
			final EnvVars envVars = this.getContext().get(EnvVars.class);

			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final boolean force = this.step.isForce();

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");

			listener.getLogger().format("Downloading s3://%s/%s to %s %n ", bucket, path, target.toURI());
			if (target.exists()) {
				if (force) {
					if (target.isDirectory()) {
						target.deleteRecursive();
					} else {
						target.delete();
					}
				} else {
					listener.getLogger().println("Download failed due to existing target file; set force=true to overwrite target file");
					throw new RuntimeException("Target exists: " + target.toURI().toString());
				}
			}
			target.act(new RemoteDownloader(Execution.this.step.createS3ClientOptions(), envVars, listener, bucket, path));
			listener.getLogger().println("Download complete");
			return null;
		}

	}

	private static class RemoteDownloader extends MasterToSlaveFileCallable<Void> {

		@Serial
		private static final long serialVersionUID = 1L;

		private final S3ClientOptions amazonS3ClientOptions;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;

		RemoteDownloader(S3ClientOptions amazonS3ClientOptions, EnvVars envVars, TaskListener taskListener, String bucket, String path) {
			this.amazonS3ClientOptions = amazonS3ClientOptions;
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.bucket = bucket;
			this.path = path;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			AmazonS3 s3Client = AWSClientFactory.create(this.amazonS3ClientOptions.createAmazonS3ClientBuilder(), this.envVars);
			TransferManager mgr = AWSUtilFactory.newTransferManager(s3Client);

			if (this.path == null || this.path.isEmpty() || this.path.endsWith("/")) {
				try {
					final MultipleFileDownload fileDownload = mgr.downloadDirectory(this.bucket, this.path, localFile);
					fileDownload.waitForCompletion();
					RemoteDownloader.this.taskListener.getLogger().println("Finished: " + fileDownload.getDescription());
				}
				finally {
					mgr.shutdownNow();
				}
				return null;
			} else {
				try {
					final Download download = mgr.download(this.bucket, this.path, localFile);
					download.addProgressListener((ProgressListener) progressEvent -> {
						if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							RemoteDownloader.this.taskListener.getLogger().println("Finished: " + download.getDescription());
						}
					});
					download.waitForCompletion();
				}
				finally {
					mgr.shutdownNow();
				}
				return null;
			}
		}

	}
}
