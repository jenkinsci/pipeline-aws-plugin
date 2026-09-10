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

import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FailedFileDownload;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.utils.S3Utils;
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
import java.nio.file.Path;
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

	/**
	 * v1 recreated each object's whole key under the destination directory, so downloading the
	 * prefix a/b/ into out produced out/a/b/x.txt. v2 resolves keys relative to the listing prefix
	 * instead, which would put the same object at out/x.txt - and silently: the download succeeds,
	 * and only a later step reading the file by its full key path notices. Pushing the prefix into
	 * the destination restores v1's layout, because v2 then strips it back off each key.
	 *
	 * Resolved segment by segment rather than by handing the whole prefix to resolve, so that the
	 * shapes a user-supplied path can take - a leading slash, a trailing one, a doubled separator in
	 * a//b/ - are absorbed by the empty-segment check rather than producing empty path elements.
	 */
	static Path destinationFor(File localFile, String prefix) {
		Path destination = localFile.toPath();
		for (String segment : prefix.split("/")) {
			if (!segment.isEmpty()) {
				destination = destination.resolve(segment);
			}
		}
		return destination;
	}

	/**
	 * Built here rather than inline so the layout test can issue exactly the request the step issues:
	 * the resulting on-disk layout depends on the destination and the listing prefix together, and a
	 * test that rebuilds the request itself can silently stop matching.
	 *
	 * The filter widens the SDK's own exclusion. DownloadFilter.allObjects, the default, already skips
	 * a delimiter-terminated key whose size is zero - the "folder marker" the S3 console creates - so
	 * those were never a problem. It does accept a delimiter-terminated key with content, which v2
	 * normalises to an empty relative path: that resolves to the destination directory itself, cannot
	 * be written as a file, and would become a failed transfer and so a failed build. v1 skipped every
	 * key ending in the delimiter regardless of size, and this restores that.
	 */
	static DownloadDirectoryRequest downloadDirectoryRequest(String bucket, File localFile, String prefix) {
		return DownloadDirectoryRequest.builder()
				.bucket(bucket)
				.destination(destinationFor(localFile, prefix))
				.listObjectsV2RequestTransformer(list -> list.prefix(prefix))
				.filter(s3Object -> !s3Object.key().endsWith("/"))
				.build();
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Void> {

		protected static final long serialVersionUID = 1L;

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

		protected static final long serialVersionUID = 1L;

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
			// S3TransferManager only closes an async client it created itself, so a client passed to it
			// has to be closed here - on an agent that runs many builds, leaking a netty event loop per
			// download adds up.
			try (S3AsyncClient s3Client = AWSClientFactory.create(this.amazonS3ClientOptions.createS3AsyncClientBuilderForDownload(), this.envVars);
					S3TransferManager mgr = AWSUtilFactory.newV2TransferManager(s3Client)) {
				if (this.path == null || this.path.isEmpty() || this.path.endsWith("/")) {
					this.downloadDirectory(mgr, localFile);
				} else {
					this.downloadFile(mgr, localFile);
				}
			}
			return null;
		}

		private void downloadFile(S3TransferManager mgr, File localFile) throws InterruptedException {
			S3Utils.joinTransfer(mgr.downloadFile(DownloadFileRequest.builder()
					.getObjectRequest(get -> get.bucket(this.bucket).key(this.path))
					.destination(localFile)
					.build()).completionFuture());
			this.taskListener.getLogger().println("Finished: download of s3://" + this.bucket + "/" + this.path);
		}

		/**
		 * v1's MultipleFileDownload.waitForCompletion threw if any file in the directory failed. v2
		 * completes successfully and reports the failures in failedTransfers(), so without this check
		 * a partly-downloaded directory would look like a clean download and the build would carry on
		 * with missing files.
		 */
		private void downloadDirectory(S3TransferManager mgr, File localFile) throws InterruptedException {
			String prefix = this.path == null ? "" : this.path;
			CompletedDirectoryDownload completed = S3Utils.joinTransfer(mgr.downloadDirectory(
					S3DownloadStep.downloadDirectoryRequest(this.bucket, localFile, prefix)).completionFuture());

			if (!completed.failedTransfers().isEmpty()) {
				for (FailedFileDownload failure : completed.failedTransfers()) {
					this.taskListener.getLogger().println("Failed: " + failure);
				}
				throw new RuntimeException("Download of s3://" + this.bucket + "/" + prefix + " failed for "
						+ completed.failedTransfers().size() + " object(s); see the log above");
			}
			this.taskListener.getLogger().println("Finished: download of s3://" + this.bucket + "/" + prefix);
		}

	}
}
