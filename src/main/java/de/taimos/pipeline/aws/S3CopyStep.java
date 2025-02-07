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
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.SSEAwsKeyManagementParams;
import com.amazonaws.services.s3.transfer.Copy;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import jakarta.annotation.Nonnull;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class S3CopyStep extends AbstractS3Step {

	private final String fromBucket;
	private final String fromPath;
	private final String toBucket;
	private final String toPath;
	private String kmsId;
	private String[] metadatas;
	private CannedAccessControlList acl;
	private String cacheControl;
	private String contentType;
	private String contentDisposition;
	private String sseAlgorithm;

	@DataBoundConstructor
	public S3CopyStep(String fromBucket, String fromPath, String toBucket, String toPath, boolean pathStyleAccessEnabled, boolean payloadSigningEnabled) {
		super(pathStyleAccessEnabled, payloadSigningEnabled);
		this.fromBucket = fromBucket;
		this.fromPath = fromPath;
		this.toBucket = toBucket;
		this.toPath = toPath;
	}

	public String getFromBucket() {
		return this.fromBucket;
	}

	public String getFromPath() {
		return this.fromPath;
	}

	public String getToBucket() {
		return this.toBucket;
	}

	public String getToPath() {
		return this.toPath;
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

	public static class Execution extends SynchronousNonBlockingStepExecution<String> {

		@Serial
		private static final long serialVersionUID = 1L;

		protected final transient S3CopyStep step;

		public Execution(S3CopyStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public String run() throws Exception {
			final String fromBucket = this.step.getFromBucket();
			final String toBucket = this.step.getToBucket();
			final String fromPath = this.step.getFromPath();
			final String toPath = this.step.getToPath();
			final String kmsId = this.step.getKmsId();
			final Map<String, String> metadatas = new HashMap<>();
			final CannedAccessControlList acl = this.step.getAcl();
			final String cacheControl = this.step.getCacheControl();
			final String contentType = this.step.getContentType();
			final String contentDisposition = this.step.getContentDisposition();
			final String sseAlgorithm = this.step.getSseAlgorithm();
			final S3ClientOptions s3ClientOptions = this.step.createS3ClientOptions();
			final EnvVars envVars = this.getContext().get(EnvVars.class);

			if (this.step.getMetadatas() != null) {
				for (String metadata : this.step.getMetadatas()) {
					if (metadata.split(":").length == 2) {
						metadatas.put(metadata.split(":")[0], metadata.split(":")[1]);
					}
				}
			}

			Preconditions.checkArgument(fromBucket != null && !fromBucket.isEmpty(), "From bucket must not be null or empty");
			Preconditions.checkArgument(fromPath != null && !fromPath.isEmpty(), "From path must not be null or empty");
			Preconditions.checkArgument(toBucket != null && !toBucket.isEmpty(), "To bucket must not be null or empty");
			Preconditions.checkArgument(toPath != null && !toPath.isEmpty(), "To path must not be null or empty");

			TaskListener listener = Execution.this.getContext().get(TaskListener.class);
			listener.getLogger().format("Copying s3://%s/%s to s3://%s/%s%n", fromBucket, fromPath, toBucket, toPath);

			CopyObjectRequest request = new CopyObjectRequest(fromBucket, fromPath, toBucket, toPath);

			// Add metadata
			if (!metadatas.isEmpty() || (cacheControl != null && !cacheControl.isEmpty()) || (contentType != null && !contentType.isEmpty()) || (contentDisposition != null && !contentDisposition.isEmpty())|| (sseAlgorithm != null && !sseAlgorithm.isEmpty())) {
				ObjectMetadata metas = new ObjectMetadata();
				if (!metadatas.isEmpty()) {
					metas.setUserMetadata(metadatas);
				}
				if (cacheControl != null && !cacheControl.isEmpty()) {
					metas.setCacheControl(cacheControl);
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

			AmazonS3 s3client = AWSClientFactory.create(s3ClientOptions.createAmazonS3ClientBuilder(), this.getContext(), envVars);
			TransferManager mgr = AWSUtilFactory.newTransferManager(s3client);
			try {
				final Copy copy = mgr.copy(request);
				copy.addProgressListener((ProgressListener) progressEvent -> {
					if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
						listener.getLogger().println("Finished: " + copy.getDescription());
					}
				});
				copy.waitForCompletion();
			}
			finally{
				mgr.shutdownNow();
			}

			listener.getLogger().println("Copy complete");
			return String.format("s3://%s/%s", toBucket, toPath);
		}

	}

}
