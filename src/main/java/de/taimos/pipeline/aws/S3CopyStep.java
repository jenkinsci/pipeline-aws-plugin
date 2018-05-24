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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.transfer.Copy;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.utils.StepUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;

public class S3CopyStep extends AbstractS3Step {

	private final String sourceBucket;
	private final String sourcePath;
	private final String destinationBucket;
	private final String destinationPath;
	private String kmsId;
	private String[] metadatas;
	private CannedAccessControlList acl;
	private String cacheControl;
	private String contentType;
	private String sseAlgorithm;

	@DataBoundConstructor
	public S3CopyStep(String sourceBucket, String sourcePath, String destinationBucket, String destinationPath, boolean pathStyleAccessEnabled, boolean payloadSigningEnabled) {
		super(pathStyleAccessEnabled, payloadSigningEnabled);
		this.sourceBucket = sourceBucket;
		this.sourcePath = sourcePath;
		this.destinationBucket = destinationBucket;
		this.destinationPath = destinationPath;
	}

	public String getSourceBucket() {
		return this.sourceBucket;
	}

	public String getSourcePath() {
		return this.sourcePath;
	}

	public String getDestinationBucket() {
		return this.destinationBucket;
	}

	public String getDestinationPath() {
		return this.destinationPath;
	}

	public String getKmsId() {
		return this.kmsId;
	}

	@DataBoundSetter
	public void setKmsId(String kmsId) {
		this.kmsId = kmsId;
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

	public String getSseAlgorithm() {
		return this.sseAlgorithm;
	}

	@DataBoundSetter
	public void setSseAlgorithm(String sseAlgorithm) {
		this.sseAlgorithm = sseAlgorithm;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new S3CopyStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "s3Copy";
		}

		@Override
		@Nonnull
		public String getDisplayName() {
			return "Copy file between S3 buckets";
		}
	}

	public static class Execution extends StepExecution {

		protected static final long serialVersionUID = 1L;

		protected final transient S3CopyStep step;

		public Execution(S3CopyStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public boolean start() throws Exception {
			final String sourceBucket = this.step.getSourceBucket();
			final String destinationBucket = this.step.getDestinationBucket();
			final String sourcePath = this.step.getSourcePath();
			final String destinationPath = this.step.getDestinationPath();
			final String kmsId = this.step.getKmsId();
			final Map<String, String> metadatas = new HashMap<>();
			final CannedAccessControlList acl = this.step.getAcl();
			final String cacheControl = this.step.getCacheControl();
			final String contentType = this.step.getContentType();
			final String sseAlgorithm = this.step.getSseAlgorithm();
			final S3ClientOptions s3ClientOptions = this.step.createS3ClientOptions();
			final EnvVars envVars = this.getContext().get(EnvVars.class);

			if (this.step.getMetadatas() != null && this.step.getMetadatas().length != 0) {
				for (String metadata : this.step.getMetadatas()) {
					if (metadata.split(":").length == 2) {
						metadatas.put(metadata.split(":")[0], metadata.split(":")[1]);
					}
				}
			}

			Preconditions.checkArgument(sourceBucket != null && !sourceBucket.isEmpty(), "Source bucket must not be null or empty");
			Preconditions.checkArgument(sourcePath != null && !sourcePath.isEmpty(), "Source path must not be null or empty");
			Preconditions.checkArgument(destinationBucket != null && !destinationBucket.isEmpty(), "Destination bucket must not be null or empty");
			Preconditions.checkArgument(destinationPath != null && !destinationPath.isEmpty(), "Destination path must not be null or empty");

			new Thread("s3Copy") {
				@Override
				@SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "RuntimeExceptions need to be catched")
				public void run() {
					try {
						TaskListener listener = Execution.this.getContext().get(TaskListener.class);
						listener.getLogger().format("Copying s3://%s/%s to s3://%s/%s%n", sourceBucket, sourcePath, destinationBucket, destinationPath);

						CopyObjectRequest request = new CopyObjectRequest(sourceBucket, sourcePath, destinationBucket, destinationPath);

						// Add metadata
						if (metadatas.size() > 0 || (cacheControl != null && !cacheControl.isEmpty()) || (contentType != null && !contentType.isEmpty()) || (sseAlgorithm != null && !sseAlgorithm.isEmpty())) {
							ObjectMetadata metas = new ObjectMetadata();
							if (metadatas.size() > 0) {
								metas.setUserMetadata(metadatas);
							}
							if (cacheControl != null && !cacheControl.isEmpty()) {
								metas.setCacheControl(cacheControl);
							}
							if (contentType != null && !contentType.isEmpty()) {
								metas.setContentType(contentType);
							}
							if (sseAlgorithm != null && !sseAlgorithm.isEmpty()) {
								metas.setSSEAlgorithm(sseAlgorithm);
							}
							request.withNewObjectMetadata(metas);
						}

						// Add acl
						if (acl != null) {
							request.withCannedAccessControlList(acl);
						}

						// Add kms
						if (kmsId != null && !kmsId.isEmpty()) {
							listener.getLogger().format("Using KMS: %s%n", kmsId);
							request.withSSEAwsKeyManagementParams(new SSEAwsKeyManagementParams(kmsId));
						}

						TransferManager mgr = TransferManagerBuilder.standard()
							.withS3Client(AWSClientFactory.create(s3ClientOptions.createAmazonS3ClientBuilder(), envVars))
							.build();
						final Copy copy = mgr.copy(request);
						copy.addProgressListener((ProgressListener) progressEvent -> {
							if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
								listener.getLogger().println("Finished: " + copy.getDescription());
							}
						});
						copy.waitForCompletion();

						listener.getLogger().println("Copy complete");
						Execution.this.getContext().onSuccess(String.format("s3://%s/%s", destinationBucket, destinationPath));
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

}
