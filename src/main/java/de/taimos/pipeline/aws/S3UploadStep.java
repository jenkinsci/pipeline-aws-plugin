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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAlgorithm;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

public class S3UploadStep extends AbstractS3Step {

	private final String bucket;
	private String file;
	private String path = "";
	private String kmsId;
	private String includePathPattern;
	private String excludePathPattern;
	private String workingDir;
	private String[] metadatas;
	private CannedAccessControlList acl;
	private String cacheControl;
	private String contentType;

	@DataBoundConstructor
	public S3UploadStep(String bucket, boolean pathStyleAccessEnabled, boolean payloadSigningEnabled) {
		super(pathStyleAccessEnabled, payloadSigningEnabled);
		this.bucket = bucket;
	}

	public String getFile() {
		return this.file;
	}

	@DataBoundSetter
	public void setFile(String file) {
		this.file = file;
	}

	public String getBucket() {
		return this.bucket;
	}

	public String getPath() {
		return this.path;
	}

	public String getKmsId() {
		return this.kmsId;
	}

	@DataBoundSetter
	public void setKmsId(String kmsId) {
		this.kmsId = kmsId;
	}

	@DataBoundSetter
	public void setPath(String path) {
		this.path = path;
	}

	public String getIncludePathPattern() {
		return this.includePathPattern;
	}

	@DataBoundSetter
	public void setIncludePathPattern(String includePathPattern) {
		this.includePathPattern = includePathPattern;
	}

	public String getExcludePathPattern() {
		return this.excludePathPattern;
	}

	@DataBoundSetter
	public void setExcludePathPattern(String excludePathPattern) {
		this.excludePathPattern = excludePathPattern;
	}

	public String getWorkingDir() {
		return this.workingDir;
	}

	@DataBoundSetter
	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
	}

	public String[] getMetadatas() {
		if (this.metadatas != null) {
			return this.metadatas.clone();
		} else {
			return null;
		}
	}

	@DataBoundSetter
	public void setMetadatas(String[] metadatas) {
		if (metadatas != null) {
			this.metadatas = metadatas.clone();
		} else {
			this.metadatas = null;
		}
	}

	public CannedAccessControlList getAcl() {
		return this.acl;
	}

	@DataBoundSetter
	public void setAcl(CannedAccessControlList acl) {
		this.acl = acl;
	}

	public String getCacheControl() {
		return this.cacheControl;
	}

	@DataBoundSetter
	public void setCacheControl(final String cacheControl) {
		this.cacheControl = cacheControl;
	}

	public String getContentType() {
		return this.contentType;
	}

	@DataBoundSetter
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new S3UploadStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
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

	public static class Execution extends StepExecution {

		protected static final long serialVersionUID = 1L;

		protected final transient S3UploadStep step;

		public Execution(S3UploadStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public boolean start() throws Exception {
			final String file = this.step.getFile();
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final String kmsId = this.step.getKmsId();
			final String includePathPattern = this.step.getIncludePathPattern();
			final String excludePathPattern = this.step.getExcludePathPattern();
			final String workingDir = this.step.getWorkingDir();
			final Map<String, String> metadatas = new HashMap<>();
			final CannedAccessControlList acl = this.step.getAcl();
			final String cacheControl = this.step.getCacheControl();
			final String contentType = this.step.getContentType();

			if (this.step.getMetadatas() != null && this.step.getMetadatas().length != 0) {
				for (String metadata : this.step.getMetadatas()) {
					if (metadata.split(":").length == 2) {
						metadatas.put(metadata.split(":")[0], metadata.split(":")[1]);
					}
				}
			}

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			Preconditions.checkArgument(file != null || includePathPattern != null, "File or IncludePathPattern must not be null");
			Preconditions.checkArgument(includePathPattern == null || file == null, "File and IncludePathPattern cannot be use together");

			final List<FilePath> children = new ArrayList<>();
			final FilePath dir;
			if (workingDir != null && !"".equals(workingDir.trim())) {
				dir = this.getContext().get(FilePath.class).child(workingDir);
			} else {
				dir = this.getContext().get(FilePath.class);
			}
			if (file != null) {
				children.add(dir.child(file));
			} else if (excludePathPattern != null && !excludePathPattern.trim().isEmpty()) {
				children.addAll(Arrays.asList(dir.list(includePathPattern, excludePathPattern, true)));
			} else {
				children.addAll(Arrays.asList(dir.list(includePathPattern, null, true)));

			}

			new Thread("s3Upload") {
				@Override
				@SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "RuntimeExceptions need to be catched")
				public void run() {
					try {
						TaskListener listener = Execution.this.getContext().get(TaskListener.class);
						if (children.isEmpty()) {
							listener.getLogger().println("Nothing to upload");
							Execution.this.getContext().onSuccess(null);
						} else if (children.size() == 1) {
							FilePath child = children.get(0);
							listener.getLogger().format("Uploading %s to s3://%s/%s %n", child.toURI(), bucket, path);
							if (!child.exists()) {
								listener.getLogger().println("Upload failed due to missing source file");
								Execution.this.getContext().onFailure(new FileNotFoundException(child.toURI().toString()));
								return;
							}

							child.act(new RemoteUploader(Execution.this.step.createS3ClientOptions(), Execution.this.getContext().get(EnvVars.class), listener, bucket, path, metadatas, acl, cacheControl, contentType, kmsId));

							listener.getLogger().println("Upload complete");
							Execution.this.getContext().onSuccess(String.format("s3://%s/%s", bucket, path));
						} else {
							List<File> fileList = new ArrayList<>();
							listener.getLogger().format("Uploading %s to s3://%s/%s %n", includePathPattern, bucket, path);
							for (FilePath child : children) {
								child.act(new FeedList(fileList));
							}
							dir.act(new RemoteListUploader(Execution.this.step.createS3ClientOptions(), Execution.this.getContext().get(EnvVars.class), listener, fileList, bucket, path, metadatas, acl, cacheControl, contentType, kmsId));
							listener.getLogger().println("Upload complete");
							Execution.this.getContext().onSuccess(String.format("s3://%s/%s", bucket, path));
						}
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

	private static class RemoteUploader extends MasterToSlaveFileCallable<Void> {

		protected static final long serialVersionUID = 1L;
		private final S3ClientOptions amazonS3ClientOptions;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private final Map<String, String> metadatas;
		private final CannedAccessControlList acl;
		private final String cacheControl;
		private final String contentType;
		private final String kmsId;

		RemoteUploader(S3ClientOptions amazonS3ClientOptions, EnvVars envVars, TaskListener taskListener, String bucket, String path, Map<String, String> metadatas, CannedAccessControlList acl, String cacheControl, String contentType, String kmsId) {
			this.amazonS3ClientOptions = amazonS3ClientOptions;
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.bucket = bucket;
			this.path = path;
			this.metadatas = metadatas;
			this.acl = acl;
			this.cacheControl = cacheControl;
			this.contentType = contentType;
			this.kmsId = kmsId;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			TransferManager mgr = TransferManagerBuilder.standard()
					.withS3Client(AWSClientFactory.create(this.amazonS3ClientOptions.createAmazonS3ClientBuilder(), this.envVars))
					.build();
			if (localFile.isFile()) {
				Preconditions.checkArgument(this.path != null && !this.path.isEmpty(), "Path must not be null or empty when uploading file");
				String path = this.path;
				if (path.endsWith("/")) {
					path += localFile.getName();
				}
				PutObjectRequest request = new PutObjectRequest(this.bucket, path, localFile);

				// Add metadata
				if ((this.metadatas != null && this.metadatas.size() > 0) || (this.cacheControl != null && !this.cacheControl.isEmpty()) || (this.contentType != null && !this.contentType.isEmpty())) {
					ObjectMetadata metas = new ObjectMetadata();
					if (this.metadatas != null && this.metadatas.size() > 0) {
						metas.setUserMetadata(this.metadatas);
					}
					if (this.cacheControl != null && !this.cacheControl.isEmpty()) {
						metas.setCacheControl(this.cacheControl);
					}
					if (this.contentType != null && !this.contentType.isEmpty()) {
						metas.setContentType(this.contentType);
					}
					request.withMetadata(metas);
				}

				// Add acl
				if (this.acl != null) {
					request.withCannedAcl(this.acl);
				}

				// Add kms
				if (this.kmsId != null && !this.kmsId.isEmpty()) {
					RemoteUploader.this.taskListener.getLogger().format("Using KMS: %s%n", this.kmsId);
					request.withSSEAwsKeyManagementParams(new SSEAwsKeyManagementParams(this.kmsId));
				}

				final Upload upload = mgr.upload(request);
				upload.addProgressListener(new ProgressListener() {
					@Override
					public void progressChanged(ProgressEvent progressEvent) {
						if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							RemoteUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
						}
					}
				});
				upload.waitForCompletion();
				return null;
			}
			if (localFile.isDirectory()) {
				final MultipleFileUpload fileUpload;
				final ObjectMetadataProvider metadatasProvider = new ObjectMetadataProvider() {
					@Override
					public void provideObjectMetadata(File file, ObjectMetadata meta) {
						if (meta != null) {
							if (RemoteUploader.this.metadatas != null && RemoteUploader.this.metadatas.size() > 0) {
								meta.setUserMetadata(RemoteUploader.this.metadatas);
							}
							if (RemoteUploader.this.acl != null) {
								meta.setHeader(Headers.S3_CANNED_ACL, RemoteUploader.this.acl);
							}
							if (RemoteUploader.this.cacheControl != null && !RemoteUploader.this.cacheControl.isEmpty()) {
								meta.setCacheControl(RemoteUploader.this.cacheControl);
							}
							if (RemoteUploader.this.contentType != null && !RemoteUploader.this.contentType.isEmpty()) {
								meta.setContentType(RemoteUploader.this.contentType);
							}
							if (RemoteUploader.this.kmsId != null && !RemoteUploader.this.kmsId.isEmpty()) {
								final SSEAwsKeyManagementParams sseAwsKeyManagementParams = new SSEAwsKeyManagementParams(RemoteUploader.this.kmsId);
								meta.setSSEAlgorithm(SSEAlgorithm.KMS.getAlgorithm());
								meta.setHeader(
										Headers.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEYID,
										sseAwsKeyManagementParams.getAwsKmsKeyId()
								);
							}
						}
					}
				};
				fileUpload = mgr.uploadDirectory(this.bucket, this.path, localFile, true, metadatasProvider);
				for (final Upload upload : fileUpload.getSubTransfers()) {
					upload.addProgressListener(new ProgressListener() {
						@Override
						public void progressChanged(ProgressEvent progressEvent) {
							if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
								RemoteUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
							}
						}
					});
				}
				fileUpload.waitForCompletion();
				return null;
			}
			return null;
		}

	}

	private static class RemoteListUploader extends MasterToSlaveFileCallable<Void> {

		protected static final long serialVersionUID = 1L;
		private final S3ClientOptions amazonS3ClientOptions;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private final List<File> fileList;
		private final Map<String, String> metadatas;
		private final CannedAccessControlList acl;
		private final String cacheControl;
		private final String contentType;
		private final String kmsId;

		RemoteListUploader(S3ClientOptions amazonS3ClientOptions, EnvVars envVars, TaskListener taskListener, List<File> fileList, String bucket, String path, Map<String, String> metadatas, CannedAccessControlList acl, final String cacheControl, final String contentType, String kmsId) {
			this.amazonS3ClientOptions = amazonS3ClientOptions;
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.fileList = fileList;
			this.bucket = bucket;
			this.path = path;
			this.metadatas = metadatas;
			this.acl = acl;
			this.cacheControl = cacheControl;
			this.contentType = contentType;
			this.kmsId = kmsId;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			TransferManager mgr = TransferManagerBuilder.standard()
					.withS3Client(AWSClientFactory.create(this.amazonS3ClientOptions.createAmazonS3ClientBuilder(), this.envVars))
					.build();
			final MultipleFileUpload fileUpload;
			ObjectMetadataProvider metadatasProvider = new ObjectMetadataProvider() {
				@Override
				public void provideObjectMetadata(File file, ObjectMetadata meta) {
					if (meta != null) {
						if (RemoteListUploader.this.metadatas != null && RemoteListUploader.this.metadatas.size() > 0) {
							meta.setUserMetadata(RemoteListUploader.this.metadatas);
						}
						if (RemoteListUploader.this.acl != null) {
							meta.setHeader(Headers.S3_CANNED_ACL, RemoteListUploader.this.acl);
						}
						if (RemoteListUploader.this.cacheControl != null && !RemoteListUploader.this.cacheControl.isEmpty()) {
							meta.setCacheControl(RemoteListUploader.this.cacheControl);
						}
						if (RemoteListUploader.this.contentType != null && !RemoteListUploader.this.contentType.isEmpty()) {
							meta.setContentType(RemoteListUploader.this.contentType);
						}
						if (RemoteListUploader.this.kmsId != null && !RemoteListUploader.this.kmsId.isEmpty()) {
							final SSEAwsKeyManagementParams sseAwsKeyManagementParams = new SSEAwsKeyManagementParams(RemoteListUploader.this.kmsId);
							meta.setSSEAlgorithm(sseAwsKeyManagementParams.getAwsKmsKeyId());
							meta.setHeader(
									Headers.SERVER_SIDE_ENCRYPTION_AWS_KMS_KEYID,
									sseAwsKeyManagementParams.getAwsKmsKeyId()
							);
						}

					}
				}
			};
			fileUpload = mgr.uploadFileList(this.bucket, this.path, localFile, this.fileList, metadatasProvider);
			for (final Upload upload : fileUpload.getSubTransfers()) {
				upload.addProgressListener(new ProgressListener() {
					@Override
					public void progressChanged(ProgressEvent progressEvent) {
						if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							RemoteListUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
						}
					}
				});
			}
			fileUpload.waitForCompletion();
			return null;
		}

	}

	private static class FeedList extends MasterToSlaveFileCallable<Void> {

		private final List<File> fileList;

		FeedList(List<File> fileList) {
			this.fileList = fileList;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			this.fileList.add(localFile);
			return null;
		}

	}

}
