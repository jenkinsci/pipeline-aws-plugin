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
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.ObjectTagging;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.SSEAlgorithm;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.model.Tag;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.ObjectTaggingProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class S3UploadStep extends AbstractS3Step {

	private final String bucket;
	private String file;
	private String text;
	private String path = "";
	private String kmsId;
	private String includePathPattern;
	private String excludePathPattern;
	private String workingDir;
	private String[] metadatas;
	private String tags;
	private CannedAccessControlList acl;
	private String cacheControl;
	private String contentEncoding;
	private String contentType;
	private String contentDisposition;
	private String sseAlgorithm;
	private String redirectLocation;
	private boolean verbose = true;

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

	public String getText() {
		return this.text;
	}

	@DataBoundSetter
	public void setText(String text) {
		this.text = text;
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

	public String getRedirectLocation() {
		return this.redirectLocation;
	}

	@DataBoundSetter
	public void setRedirectLocation(String redirectLocation) {
		this.redirectLocation = redirectLocation;
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

	public String getTags() {
		if (this.tags != null) {
			return this.tags;
		} else {
			return null;
		}
	}


	@DataBoundSetter
	public void setTags(String tags) {
		if (tags != null ) {
			this.tags = tags;
		} else {
			this.tags = null;
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

	public String getContentEncoding() {
		return this.contentEncoding;
	}

	@DataBoundSetter
	public String setContentEncoding(final String contentEncoding) {
		return this.contentEncoding = contentEncoding;
	}

	public String getContentType() {
		return this.contentType;
	}

	@DataBoundSetter
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getContentDisposition() {
		return this.contentDisposition;
	}

	@DataBoundSetter
	public void setContentDisposition(String contentDisposition) {
		this.contentDisposition = contentDisposition;
	}

	public String getSseAlgorithm() {
		return this.sseAlgorithm;
	}

	@DataBoundSetter
	public void setSseAlgorithm(String sseAlgorithm) {
		this.sseAlgorithm = sseAlgorithm;
	}

	@DataBoundSetter
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public boolean getVerbose() {
		return this.verbose;
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

	public static class Execution extends SynchronousNonBlockingStepExecution<String> {

		@Serial
		private static final long serialVersionUID = 1L;

		protected final transient S3UploadStep step;

		public Execution(S3UploadStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public String run() throws Exception {
			final String file = this.step.getFile();
			final String text = this.step.getText();
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final String kmsId = this.step.getKmsId();
			final String includePathPattern = this.step.getIncludePathPattern();
			final String excludePathPattern = this.step.getExcludePathPattern();
			final String workingDir = this.step.getWorkingDir();
			final Map<String, String> metadatas = new HashMap<>();
			final Map<String, String> tags = new HashMap<>();
			final CannedAccessControlList acl = this.step.getAcl();
			final String cacheControl = this.step.getCacheControl();
			final String contentEncoding = this.step.getContentEncoding();
			final String contentType = this.step.getContentType();
			final String contentDisposition = this.step.getContentDisposition();
			final String sseAlgorithm = this.step.getSseAlgorithm();
			final String redirectLocation = this.step.getRedirectLocation();
			final boolean verbose = this.step.getVerbose();
			boolean omitSourcePath = false;
			boolean sendingText = false;

			if (this.step.getMetadatas() != null) {
				for (String metadata : this.step.getMetadatas()) {
					if (metadata.contains(":")) {
						metadatas.put(metadata.substring(0, metadata.indexOf(':')), metadata.substring(metadata.indexOf(':') + 1));
					}
				}
			}

			if (this.step.getTags() != null && !this.step.getTags().isEmpty()) {
				//[tag1:value1, tag2:value2]
				String tagsNoBraces = this.step.getTags().substring(1, this.step.getTags().length()-1);
				String[] pairs= tagsNoBraces.split(", ");
				for(String pair : pairs){
					String[] entry = pair.split(":");
					tags.put(entry[0], entry[1]);
				}
			}

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			Preconditions.checkArgument(text != null || file != null || includePathPattern != null, "At least one argument of Text, File or IncludePathPattern must be included");
			Preconditions.checkArgument(includePathPattern == null || file == null, "File and IncludePathPattern cannot be used together");
			Preconditions.checkArgument(text == null || file == null, "Text and File cannot be used together");
			Preconditions.checkArgument(includePathPattern == null || text == null, "IncludePathPattern and Text cannot be used together");

			final List<FilePath> children = new ArrayList<>();
			final FilePath dir;
			if (workingDir != null && !workingDir.trim().isEmpty()) {
				dir = this.getContext().get(FilePath.class).child(workingDir);
			} else {
				dir = this.getContext().get(FilePath.class);
			}
			if (text != null) {
				sendingText = true;
			} else if (file != null) {
				children.add(dir.child(file));
				omitSourcePath = true;
			} else if (excludePathPattern != null && !excludePathPattern.trim().isEmpty()) {
				children.addAll(Arrays.asList(dir.list(includePathPattern, excludePathPattern, true)));
			} else {
				children.addAll(Arrays.asList(dir.list(includePathPattern, null, true)));

			}

			TaskListener listener = Execution.this.getContext().get(TaskListener.class);

			if (sendingText) {
				listener.getLogger().format("Uploading text string to s3://%s/%s %n", bucket, path);

				S3ClientOptions amazonS3ClientOptions = Execution.this.step.createS3ClientOptions();
				EnvVars envVars = Execution.this.getContext().get(EnvVars.class);

				AmazonS3 s3Client = AWSClientFactory.create(amazonS3ClientOptions.createAmazonS3ClientBuilder(), Execution.this.getContext(), envVars);
				TransferManager mgr = AWSUtilFactory.newTransferManager(s3Client);

				byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
				PutObjectRequest request = null;
				ObjectMetadata metas = new ObjectMetadata();

				metas.setContentLength(bytes.length);

				// Add metadata
				if (metadatas != null && !metadatas.isEmpty()) {
					metas.setUserMetadata(metadatas);
				}
				if (cacheControl != null && !cacheControl.isEmpty()) {
					metas.setCacheControl(cacheControl);
				}
				if (contentEncoding != null && !contentEncoding.isEmpty()) {
					metas.setContentEncoding(contentEncoding);
				}
				if (contentType != null && !contentType.isEmpty()) {
					metas.setContentType(contentType);
				}
				if (contentDisposition != null && !contentDisposition.isEmpty()) {
					metas.setContentDisposition(contentDisposition);
				}
				if (sseAlgorithm != null && !sseAlgorithm.isEmpty()) {
					metas.setSSEAlgorithm(sseAlgorithm);
				}

				request = new PutObjectRequest(bucket, path, new ByteArrayInputStream(bytes), metas);

				// Add acl
				if (acl != null) {
					request.withCannedAcl(acl);
				}

				//add tags
				if(!tags.isEmpty()){
					request.withTagging(new ObjectTagging(
						tags.entrySet().stream().map(tag-> new Tag(tag.getKey(), tag.getValue())).collect(Collectors.toList())
					));
				}


				// Add kms
				if (kmsId != null && !kmsId.isEmpty()) {
					listener.getLogger().format("Using KMS: %s%n", kmsId);
					request.withSSEAwsKeyManagementParams(new SSEAwsKeyManagementParams(kmsId));
				}

				if (redirectLocation != null && !redirectLocation.isEmpty()) {
					request.withRedirectLocation(redirectLocation);
				}

				try {
					final Upload upload = mgr.upload(request);
					upload.addProgressListener((ProgressListener) progressEvent -> {
						if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							if (verbose) {
								listener.getLogger().println("Finished: " + upload.getDescription());
							}
						}
					});
					upload.waitForCompletion();
				}
				finally{
					mgr.shutdownNow();
				}

				listener.getLogger().println("Upload complete");
				return String.format("s3://%s/%s", bucket, path);
			} else if (children.isEmpty()) {
				listener.getLogger().println("Nothing to upload");
				return null;
			} else if (omitSourcePath) {
				FilePath child = children.get(0);
				listener.getLogger().format("Uploading %s to s3://%s/%s %n", child.toURI(), bucket, path);
				if (!child.exists()) {
					listener.getLogger().println("Upload failed due to missing source file");
					throw new FileNotFoundException(child.toURI().toString());
				}

				child.act(new RemoteUploader(Execution.this.step.createS3ClientOptions(), Execution.this.getContext().get(EnvVars.class), listener, bucket, path, metadatas, tags, acl, cacheControl, contentEncoding, contentType, contentDisposition, kmsId, sseAlgorithm, redirectLocation));

				listener.getLogger().println("Upload complete");
				return String.format("s3://%s/%s", bucket, path);
			} else {
				List<File> fileList = new ArrayList<>();
				listener.getLogger().format("Uploading %s to s3://%s/%s %n", includePathPattern, bucket, path);
				for (FilePath child : children) {
					fileList.add(child.act(FIND_FILE_ON_SLAVE));
				}
				dir.act(new RemoteListUploader(Execution.this.step.createS3ClientOptions(), Execution.this.getContext().get(EnvVars.class), listener, fileList, bucket, path, metadatas, tags, acl, cacheControl, contentEncoding, contentType, contentDisposition, kmsId, sseAlgorithm));
				listener.getLogger().println("Upload complete");
				return String.format("s3://%s/%s", bucket, path);
			}
		}

	}

	private static class RemoteUploader extends MasterToSlaveFileCallable<Void> {

		@Serial
		private static final long serialVersionUID = 1L;
		private final S3ClientOptions amazonS3ClientOptions;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private final Map<String, String> metadatas;
		private final Map<String, String> tags;
		private final CannedAccessControlList acl;
		private final String cacheControl;
		private final String contentEncoding;
		private final String contentType;
		private final String contentDisposition;
		private final String kmsId;
		private final String sseAlgorithm;
		private final String redirectLocation;

		RemoteUploader(S3ClientOptions amazonS3ClientOptions, EnvVars envVars, TaskListener taskListener, String bucket, String path, Map<String, String> metadatas, Map<String, String> tags, CannedAccessControlList acl, String cacheControl, String contentEncoding, String contentType, String contentDisposition, String kmsId, String sseAlgorithm, String redirectLocation) {
			this.amazonS3ClientOptions = amazonS3ClientOptions;
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.bucket = bucket;
			this.path = path;
			this.metadatas = metadatas;
			this.tags=tags;
			this.acl = acl;
			this.cacheControl = cacheControl;
			this.contentEncoding = contentEncoding;
			this.contentType = contentType;
			this.contentDisposition = contentDisposition;
			this.kmsId = kmsId;
			this.sseAlgorithm = sseAlgorithm;
			this.redirectLocation = redirectLocation;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			AmazonS3 s3Client = AWSClientFactory.create(this.amazonS3ClientOptions.createAmazonS3ClientBuilder(), this.envVars);
			TransferManager mgr = AWSUtilFactory.newTransferManager(s3Client);
			if (localFile.isFile()) {
				String path = this.path;
				if (path.endsWith("/") || path.isEmpty()) {
					path += localFile.getName();
				}
				PutObjectRequest request = new PutObjectRequest(this.bucket, path, localFile);

				// Add metadata
				if ((this.metadatas != null && !this.metadatas.isEmpty()) || (this.cacheControl != null && !this.cacheControl.isEmpty()) || (this.contentEncoding != null && !this.contentEncoding.isEmpty()) || (this.contentType != null && !this.contentType.isEmpty()) || (this.contentDisposition != null && !this.contentDisposition.isEmpty()) || (this.sseAlgorithm != null && !this.sseAlgorithm.isEmpty())) {
					ObjectMetadata metas = new ObjectMetadata();
					if (this.metadatas != null && !this.metadatas.isEmpty()) {
						metas.setUserMetadata(this.metadatas);
					}
					if (this.cacheControl != null && !this.cacheControl.isEmpty()) {
						metas.setCacheControl(this.cacheControl);
					}
					if (this.contentEncoding != null && !this.contentEncoding.isEmpty()) {
						metas.setContentEncoding(this.contentEncoding);
					}
					if (this.contentType != null && !this.contentType.isEmpty()) {
						metas.setContentType(this.contentType);
					}
					if (this.contentDisposition != null && !this.contentDisposition.isEmpty()) {
						metas.setContentDisposition(this.contentDisposition);
					}
					if (this.sseAlgorithm != null && !this.sseAlgorithm.isEmpty()) {
						metas.setSSEAlgorithm(this.sseAlgorithm);
					}
					request.withMetadata(metas);
				}

				//add tags
				if(!tags.isEmpty()){
					request.withTagging(new ObjectTagging(
						tags.entrySet().stream().map(tag-> new Tag(tag.getKey(), tag.getValue())).collect(Collectors.toList())
					));
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

				if (this.redirectLocation != null && !this.redirectLocation.isEmpty()) {
					request.withRedirectLocation(this.redirectLocation);
				}

				try {
					final Upload upload = mgr.upload(request);
					upload.addProgressListener((ProgressListener) progressEvent -> {
						if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							RemoteUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
						}
					});
					upload.waitForCompletion();
				}
				finally {
					mgr.shutdownNow();
				}
				return null;
			}
			if (localFile.isDirectory()) {
				final MultipleFileUpload fileUpload;
				final ObjectMetadataProvider metadatasProvider = (file, meta) -> {
					if (meta != null) {
						if (RemoteUploader.this.metadatas != null && !RemoteUploader.this.metadatas.isEmpty()) {
							meta.setUserMetadata(RemoteUploader.this.metadatas);
						}
						if (RemoteUploader.this.acl != null) {
							meta.setHeader(Headers.S3_CANNED_ACL, RemoteUploader.this.acl);
						}
						if (RemoteUploader.this.cacheControl != null && !RemoteUploader.this.cacheControl.isEmpty()) {
							meta.setCacheControl(RemoteUploader.this.cacheControl);
						}
						if (RemoteUploader.this.contentEncoding != null && !RemoteUploader.this.contentEncoding.isEmpty()) {
							meta.setContentEncoding(RemoteUploader.this.contentEncoding);
						}
						if (RemoteUploader.this.contentType != null && !RemoteUploader.this.contentType.isEmpty()) {
							meta.setContentType(RemoteUploader.this.contentType);
						}
						if (RemoteUploader.this.contentDisposition != null && !RemoteUploader.this.contentDisposition.isEmpty()) {
							meta.setContentDisposition(RemoteUploader.this.contentDisposition);
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
				};

				ObjectTaggingProvider objectTaggingProvider =(uploadContext) -> {
					List<Tag> tagList = new ArrayList<>();

					//add tags
					if(tags != null){
						for (Map.Entry<String, String> entry : tags.entrySet()) {
							Tag tag = new Tag(entry.getKey(), entry.getValue());
							tagList.add(tag);
						}
					}
					return new ObjectTagging(tagList);
				};

				try {
					fileUpload = mgr.uploadDirectory(this.bucket, this.path, localFile, true, metadatasProvider, objectTaggingProvider);
					for (final Upload upload : fileUpload.getSubTransfers()) {
						upload.addProgressListener((ProgressListener) progressEvent -> {
							if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
								RemoteUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
							}
						});
					}
					fileUpload.waitForCompletion();
				}
				finally {
					mgr.shutdownNow();
				}
				return null;
			}
			return null;
		}

	}

	private static class RemoteListUploader extends MasterToSlaveFileCallable<Void> {

		@Serial
		private static final long serialVersionUID = 1L;
		private final S3ClientOptions amazonS3ClientOptions;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private final List<File> fileList;
		private final Map<String, String> metadatas;
		private final Map<String, String> tags;
		private final CannedAccessControlList acl;
		private final String cacheControl;
		private final String contentEncoding;
		private final String contentType;
		private final String contentDisposition;
		private final String kmsId;
		private final String sseAlgorithm;

		RemoteListUploader(S3ClientOptions amazonS3ClientOptions, EnvVars envVars, TaskListener taskListener, List<File> fileList, String bucket, String path, Map<String, String> metadatas, Map<String, String> tags, CannedAccessControlList acl, final String cacheControl, final String contentEncoding, final String contentType, final String contentDisposition, String kmsId, String sseAlgorithm) {
			this.amazonS3ClientOptions = amazonS3ClientOptions;
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.fileList = fileList;
			this.bucket = bucket;
			this.path = path;
			this.metadatas = metadatas;
			this.tags = tags;
			this.acl = acl;
			this.cacheControl = cacheControl;
			this.contentEncoding = contentEncoding;
			this.contentType = contentType;
			this.contentDisposition = contentDisposition;
			this.kmsId = kmsId;
			this.sseAlgorithm = sseAlgorithm;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			AmazonS3 s3Client = AWSClientFactory.create(this.amazonS3ClientOptions.createAmazonS3ClientBuilder(), this.envVars);
			TransferManager mgr = AWSUtilFactory.newTransferManager(s3Client);
			final MultipleFileUpload fileUpload;
			ObjectMetadataProvider metadatasProvider = (file, meta) -> {
				if (meta != null) {
					if (RemoteListUploader.this.metadatas != null && !RemoteListUploader.this.metadatas.isEmpty()) {
						meta.setUserMetadata(RemoteListUploader.this.metadatas);
					}
					if (RemoteListUploader.this.acl != null) {
						meta.setHeader(Headers.S3_CANNED_ACL, RemoteListUploader.this.acl);
					}
					if (RemoteListUploader.this.cacheControl != null && !RemoteListUploader.this.cacheControl.isEmpty()) {
						meta.setCacheControl(RemoteListUploader.this.cacheControl);
					}
					if (RemoteListUploader.this.contentEncoding != null && !RemoteListUploader.this.contentEncoding.isEmpty()) {
						meta.setContentEncoding(RemoteListUploader.this.contentEncoding);
					}
					if (RemoteListUploader.this.contentType != null && !RemoteListUploader.this.contentType.isEmpty()) {
						meta.setContentType(RemoteListUploader.this.contentType);
					}
					if (RemoteListUploader.this.contentDisposition != null && !RemoteListUploader.this.contentDisposition.isEmpty()) {
						meta.setContentDisposition(RemoteListUploader.this.contentDisposition);
					}
					if (RemoteListUploader.this.sseAlgorithm != null && !RemoteListUploader.this.sseAlgorithm.isEmpty()) {
						meta.setSSEAlgorithm(RemoteListUploader.this.sseAlgorithm);
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
			};

			ObjectTaggingProvider objectTaggingProvider =(uploadContext) -> {
				List<Tag> tagList = new ArrayList<>();

				//add tags
				if(tags != null){
					for (Map.Entry<String, String> entry : tags.entrySet()) {
						Tag tag = new Tag(entry.getKey(), entry.getValue());
						tagList.add(tag);
					}
				}
				return new ObjectTagging(tagList);
			};

			try {
				fileUpload = mgr.uploadFileList(this.bucket, this.path, localFile, this.fileList, metadatasProvider, objectTaggingProvider);
				for (final Upload upload : fileUpload.getSubTransfers()) {
					upload.addProgressListener((ProgressListener) progressEvent -> {
						if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							RemoteListUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
						}
					});
				}
				fileUpload.waitForCompletion();
			}
			finally {
				mgr.shutdownNow();
			}
			return null;
		}
	}

	private static MasterToSlaveFileCallable<File> FIND_FILE_ON_SLAVE = new MasterToSlaveFileCallable<>() {
		@Override
		public File invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			return localFile;
		}
	};

}
