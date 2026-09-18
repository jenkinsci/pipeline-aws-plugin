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

import de.taimos.pipeline.aws.utils.CannedAcl;
import de.taimos.pipeline.aws.utils.S3Utils;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryUpload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FailedFileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadDirectoryRequest;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.model.UploadRequest;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
	private CannedAcl acl;
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

	public CannedAcl getAcl() {
		return this.acl;
	}

	@DataBoundSetter
	public void setAcl(CannedAcl acl) {
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

		protected static final long serialVersionUID = 1L;

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
			final Map<String, String> tags = new HashMap<String, String>();
			final CannedAcl acl = this.step.getAcl();
			final String cacheControl = this.step.getCacheControl();
			final String contentEncoding = this.step.getContentEncoding();
			final String contentType = this.step.getContentType();
			final String contentDisposition = this.step.getContentDisposition();
			final String sseAlgorithm = this.step.getSseAlgorithm();
			final String redirectLocation = this.step.getRedirectLocation();
			final boolean verbose = this.step.getVerbose();
			boolean omitSourcePath = false;
			boolean sendingText = false;

			if (this.step.getMetadatas() != null && this.step.getMetadatas().length != 0) {
				for (String metadata : this.step.getMetadatas()) {
					if (metadata.contains(":")) {
						metadatas.put(metadata.substring(0, metadata.indexOf(':')), metadata.substring(metadata.indexOf(':') + 1));
					}
				}
			}

			if (this.step.getTags() != null && this.step.getTags().length() != 0) {
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
			if (workingDir != null && !"".equals(workingDir.trim())) {
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

				byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
				S3UploadOptions options = new S3UploadOptions(metadatas, tags, acl, cacheControl, contentEncoding, contentType, contentDisposition, kmsId, sseAlgorithm, redirectLocation);
				if (kmsId != null && !kmsId.isEmpty()) {
					listener.getLogger().format("Using KMS: %s%n", kmsId);
				}
				PutObjectRequest request = options.applyTo(PutObjectRequest.builder()
						.bucket(bucket)
						.key(path)
						.contentLength((long) bytes.length)).build();

				// S3TransferManager only closes an async client it created itself, so a client passed to
				// it has to be closed here or its netty event loop outlives the step.
				try (S3AsyncClient s3Client = AWSClientFactory.create(amazonS3ClientOptions.createS3AsyncClientBuilderForUpload(), Execution.this.getContext(), envVars);
						S3TransferManager mgr = AWSUtilFactory.newV2TransferManager(s3Client)) {
					S3Utils.joinTransfer(mgr.upload(UploadRequest.builder()
							.putObjectRequest(request)
							.requestBody(AsyncRequestBody.fromBytes(bytes))
							.build()).completionFuture());
				}
				if (verbose) {
					listener.getLogger().println("Finished: upload of text to s3://" + bucket + "/" + path);
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

				child.act(new RemoteUploader(Execution.this.step.createS3ClientOptions(), Execution.this.getContext().get(EnvVars.class), listener, bucket, path,
						new S3UploadOptions(metadatas, tags, acl, cacheControl, contentEncoding, contentType, contentDisposition, kmsId, sseAlgorithm, redirectLocation)));

				listener.getLogger().println("Upload complete");
				return String.format("s3://%s/%s", bucket, path);
			} else {
				List<File> fileList = new ArrayList<>();
				listener.getLogger().format("Uploading %s to s3://%s/%s %n", includePathPattern, bucket, path);
				for (FilePath child : children) {
					fileList.add(child.act(FIND_FILE_ON_SLAVE));
				}
				dir.act(new RemoteListUploader(Execution.this.step.createS3ClientOptions(), Execution.this.getContext().get(EnvVars.class), listener, fileList, bucket, path,
						new S3UploadOptions(metadatas, tags, acl, cacheControl, contentEncoding, contentType, contentDisposition, kmsId, sseAlgorithm, null)));
				listener.getLogger().println("Upload complete");
				return String.format("s3://%s/%s", bucket, path);
			}
		}

	}

	/**
	 * Uploads one file, or one directory tree, from the agent.
	 *
	 * The four separate metadata blocks v1 carried are now a single S3UploadOptions, applied to a
	 * PutObjectRequest in one place. For a directory that goes through uploadFileRequestTransformer,
	 * which is v2's replacement for ObjectMetadataProvider and ObjectTaggingProvider together.
	 */
	private static class RemoteUploader extends MasterToSlaveFileCallable<Void> {

		protected static final long serialVersionUID = 1L;

		private final S3ClientOptions amazonS3ClientOptions;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private final S3UploadOptions options;

		RemoteUploader(S3ClientOptions amazonS3ClientOptions, EnvVars envVars, TaskListener taskListener, String bucket, String path, S3UploadOptions options) {
			this.amazonS3ClientOptions = amazonS3ClientOptions;
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.bucket = bucket;
			this.path = path;
			this.options = options;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			// S3TransferManager only closes an async client it created itself, so a client passed to it
			// has to be closed here - these run on agents, which stay up across many builds.
			try (S3AsyncClient s3Client = AWSClientFactory.create(this.amazonS3ClientOptions.createS3AsyncClientBuilderForUpload(), this.envVars);
					S3TransferManager mgr = AWSUtilFactory.newV2TransferManager(s3Client)) {
				if (localFile.isFile()) {
					String key = this.path;
					if (key.endsWith("/") || key.isEmpty()) {
						key += localFile.getName();
					}
					if (this.options.getKmsId() != null && !this.options.getKmsId().isEmpty()) {
						this.taskListener.getLogger().format("Using KMS: %s%n", this.options.getKmsId());
					}
					S3Utils.joinTransfer(mgr.uploadFile(UploadFileRequest.builder()
							.putObjectRequest(this.options.applyTo(
									PutObjectRequest.builder().bucket(this.bucket).key(key)).build())
							.source(localFile)
							.build()).completionFuture());
					this.taskListener.getLogger().println("Finished: upload of s3://" + this.bucket + "/" + key);
				} else if (localFile.isDirectory()) {
					uploadDirectory(mgr, this.bucket, this.path, localFile, this.options, this.taskListener);
				}
			}
			return null;
		}

	}

	/**
	 * v1's MultipleFileUpload.waitForCompletion threw if any file failed. v2 completes normally and
	 * reports them in failedTransfers(), so without this check a partial upload would look like a
	 * successful one.
	 */
	private static void uploadDirectory(S3TransferManager mgr, String bucket, String path, File localDirectory,
			S3UploadOptions options, TaskListener taskListener) throws IOException, InterruptedException {
		logKms(options, taskListener);
		UploadDirectoryRequest.Builder request = UploadDirectoryRequest.builder()
				.bucket(bucket)
				.source(localDirectory.toPath())
				// v2's replacement for ObjectMetadataProvider and ObjectTaggingProvider together.
				// The request has to be mutated rather than rebuilt: the Consumer overload of
				// putObjectRequest constructs a fresh builder, which would throw away the bucket and
				// key the transfer manager has already computed for this file and submit every upload
				// with both null.
				.uploadFileRequestTransformer(upload -> upload.putObjectRequest(
						options.applyTo(upload.build().putObjectRequest().toBuilder()).build()));
		// v1 normalised the prefix before joining it to each relative key; v2 joins with a delimiter
		// unconditionally, so passing 'artifacts/' through verbatim would name every object
		// 'artifacts//x.txt'. Shared with uploadFileList so the two paths agree.
		String prefix = trimTrailingSlash(path);
		if (!prefix.isEmpty()) {
			request.s3Prefix(prefix);
		}

		CompletedDirectoryUpload completed = S3Utils.joinTransfer(mgr.uploadDirectory(request.build()).completionFuture());
		if (!completed.failedTransfers().isEmpty()) {
			for (FailedFileUpload failure : completed.failedTransfers()) {
				taskListener.getLogger().println("Failed: " + failure);
			}
			throw new IOException("Upload to s3://" + bucket + "/" + path + " failed for "
					+ completed.failedTransfers().size() + " file(s); see the log above");
		}
		taskListener.getLogger().println("Finished: upload to s3://" + bucket + "/" + path);
	}

	/**
	 * v1 had uploadFileList for the include/exclude-pattern case. v2 has no equivalent -
	 * UploadDirectoryRequest has no filter, that is download-only - so the files are submitted
	 * individually and awaited together. Submitting before joining is what keeps them concurrent, as
	 * uploadFileList was; joining one at a time would serialise the whole upload.
	 */
	private static void uploadFileList(S3TransferManager mgr, String bucket, String path, File baseDirectory,
			List<File> fileList, S3UploadOptions options, TaskListener taskListener) throws IOException, InterruptedException {
		logKms(options, taskListener);
		Path base = baseDirectory.toPath().toAbsolutePath().normalize();
		String trimmed = trimTrailingSlash(path);
		String prefix = trimmed.isEmpty() ? "" : trimmed + "/";

		Map<String, CompletableFuture<CompletedFileUpload>> uploads = new LinkedHashMap<>();
		for (File file : fileList) {
			String key = prefix + base.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/');
			uploads.put(key, mgr.uploadFile(UploadFileRequest.builder()
					.putObjectRequest(options.applyTo(PutObjectRequest.builder().bucket(bucket).key(key)).build())
					.source(file)
					.build()).completionFuture());
		}

		List<String> failures = new ArrayList<>();
		for (Map.Entry<String, CompletableFuture<CompletedFileUpload>> upload : uploads.entrySet()) {
			try {
				// get() rather than join(): this is the path with the most transfers in flight, and
				// join() would swallow an abort's interrupt and keep uploading against a workspace
				// Jenkins believes is free.
				upload.getValue().get();
			} catch (ExecutionException e) {
				taskListener.getLogger().println("Failed: " + upload.getKey() + " - " + e.getCause());
				failures.add(upload.getKey());
			}
		}
		if (!failures.isEmpty()) {
			throw new IOException("Upload to s3://" + bucket + "/" + path + " failed for "
					+ failures.size() + " file(s); see the log above");
		}
		taskListener.getLogger().println("Finished: upload to s3://" + bucket + "/" + path);
	}

	private static String trimTrailingSlash(String path) {
		if (path == null || path.isEmpty()) {
			return "";
		}
		String trimmed = path;
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static void logKms(S3UploadOptions options, TaskListener taskListener) {
		if (options.getKmsId() != null && !options.getKmsId().isEmpty()) {
			taskListener.getLogger().format("Using KMS: %s%n", options.getKmsId());
		}
	}

	/**
	 * The include/exclude-pattern case: the controller resolves the matching files and this uploads
	 * exactly those, as a filtered directory upload.
	 */
	private static class RemoteListUploader extends MasterToSlaveFileCallable<Void> {

		protected static final long serialVersionUID = 1L;

		private final S3ClientOptions amazonS3ClientOptions;
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final List<File> fileList;
		private final String bucket;
		private final String path;
		private final S3UploadOptions options;

		RemoteListUploader(S3ClientOptions amazonS3ClientOptions, EnvVars envVars, TaskListener taskListener, List<File> fileList, String bucket, String path, S3UploadOptions options) {
			this.amazonS3ClientOptions = amazonS3ClientOptions;
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.fileList = fileList;
			this.bucket = bucket;
			this.path = path;
			this.options = options;
		}

		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			try (S3AsyncClient s3Client = AWSClientFactory.create(this.amazonS3ClientOptions.createS3AsyncClientBuilderForUpload(), this.envVars);
					S3TransferManager mgr = AWSUtilFactory.newV2TransferManager(s3Client)) {
				uploadFileList(mgr, this.bucket, this.path, localFile, this.fileList, this.options, this.taskListener);
			}
			return null;
		}
	}

	private static MasterToSlaveFileCallable<File> FIND_FILE_ON_SLAVE = new MasterToSlaveFileCallable<File>() {
		@Override
		public File invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			return localFile;
		}
	};

}
