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

import java.io.Serializable;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;

public abstract class AbstractS3Step extends Step {

	protected boolean pathStyleAccessEnabled = false;
	protected boolean payloadSigningEnabled = false;

	protected AbstractS3Step(final boolean pathStyleAccessEnabled, final boolean payloadSigningEnabled) {
		this.pathStyleAccessEnabled = pathStyleAccessEnabled;
		this.payloadSigningEnabled = payloadSigningEnabled;
	}

	public boolean isPathStyleAccessEnabled() {
		return this.pathStyleAccessEnabled;
	}

	@DataBoundSetter
	public void setPathStyleAccessEnabled(final boolean pathStyleAccessEnabled) {
		this.pathStyleAccessEnabled = pathStyleAccessEnabled;
	}

	public boolean isPayloadSigningEnabled() {
		return this.payloadSigningEnabled;
	}

	@DataBoundSetter
	public void setPayloadSigningEnabled(final boolean payloadSigningEnabled) {
		this.payloadSigningEnabled = payloadSigningEnabled;
	}

	protected S3ClientOptions createS3ClientOptions() {
		S3ClientOptions options = new S3ClientOptions();
		options.setPathStyleAccessEnabled(this.isPathStyleAccessEnabled());
		options.setPayloadSigningEnabled(this.isPayloadSigningEnabled());
		return options;
	}

	public static class S3ClientOptions implements Serializable {
		private boolean pathStyleAccessEnabled = false;
		private boolean payloadSigningEnabled = false;

		public boolean isPathStyleAccessEnabled() {
			return this.pathStyleAccessEnabled;
		}

		public void setPathStyleAccessEnabled(final boolean pathStyleAccessEnabled) {
			this.pathStyleAccessEnabled = pathStyleAccessEnabled;
		}

		public boolean isPayloadSigningEnabled() {
			return this.payloadSigningEnabled;
		}

		public void setPayloadSigningEnabled(final boolean payloadSigningEnabled) {
			this.payloadSigningEnabled = payloadSigningEnabled;
		}

		public S3ClientBuilder createS3ClientBuilder() {
			return this.applyTo(S3Client.builder());
		}

		/*
		 * v1's TransferManagerConfiguration defaults, read off the v1 jar rather than assumed. Both the
		 * threshold and the part size differed between uploads and copies - copy parts are transferred
		 * server-side, so v1 used a much coarser size for them - and v2 carries one MultipartConfiguration
		 * per client, so the two operations need separately configured clients rather than one shared
		 * setting.
		 *
		 * The thresholds matter beyond large-object support: a multipart object's ETag is a hash of part
		 * hashes with a -N suffix rather than the content MD5, so a pipeline comparing an ETag against a
		 * locally computed digest sees a different answer once an object crosses the threshold. The SDK's
		 * own default of 8 MiB for everything would have moved that boundary for both operations.
		 *
		 * The part size matters for request count: v2 sizes copy parts as
		 * max(minimumPartSizeInBytes, ceil(length / 10000)), so the 5 MiB upload figure applied to a copy
		 * would split a 5 GiB object into roughly 1024 UploadPartCopy calls where v1 issued about 52.
		 */
		private static final long MULTIPART_UPLOAD_THRESHOLD_BYTES = 16L * 1024 * 1024;
		private static final long MULTIPART_UPLOAD_PART_SIZE_BYTES = 5L * 1024 * 1024;
		private static final long MULTIPART_COPY_THRESHOLD_BYTES = 5L * 1024 * 1024 * 1024;
		private static final long MULTIPART_COPY_PART_SIZE_BYTES = 100L * 1024 * 1024;

		static MultipartConfiguration uploadMultipartConfiguration() {
			return multipartConfiguration(MULTIPART_UPLOAD_THRESHOLD_BYTES, MULTIPART_UPLOAD_PART_SIZE_BYTES);
		}

		static MultipartConfiguration copyMultipartConfiguration() {
			return multipartConfiguration(MULTIPART_COPY_THRESHOLD_BYTES, MULTIPART_COPY_PART_SIZE_BYTES);
		}

		private static MultipartConfiguration multipartConfiguration(long thresholdBytes, long partSizeBytes) {
			return MultipartConfiguration.builder()
					.thresholdInBytes(thresholdBytes)
					.minimumPartSizeInBytes(partSizeBytes)
					.build();
		}

		/*
		 * These are only for S3TransferManager, which has no synchronous form.
		 *
		 * One per operation rather than a single method taking the numbers as parameters: the upload
		 * and copy figures differ by three orders of magnitude, and a call site passing the wrong one
		 * would be a single-token slip with no visible symptom. There is deliberately no unqualified
		 * overload either - one would become the default a new call site reaches for, which
		 * reintroduces the same slip by omission.
		 */

		/**
		 * multipartEnabled is not the SDK default, and without it the transfer manager issues a single
		 * CopyObject, which S3 rejects above 5 GiB - so copies that worked under v1 would start failing.
		 */
		public S3AsyncClientBuilder createS3AsyncClientBuilderForCopy() {
			return this.createS3AsyncClientBuilder(copyMultipartConfiguration());
		}

		/**
		 * As for copies: without multipartEnabled a single PutObject is issued, which S3 rejects above
		 * 5 GiB.
		 */
		public S3AsyncClientBuilder createS3AsyncClientBuilderForUpload() {
			return this.createS3AsyncClientBuilder(uploadMultipartConfiguration());
		}

		/**
		 * Null: downloads deliberately do not enable multipart, which is what makes this a separate
		 * factory from the upload one rather than a delegation to it.
		 *
		 * v1 had no multipart download - TransferManagerConfiguration carries thresholds and part sizes
		 * for uploads and copies only - so a download was one GetObject however large the object was.
		 * SDK v2 does support it (DownloadObjectHelper, MultipartDownloaderSubscriber), fetching the
		 * object part by part: one GetObject per part it was uploaded with, discovered from the first
		 * response's partsCount. The configured part size does not size those requests, and an object
		 * stored by a single PutObject still comes back in one. Turning it on would therefore multiply
		 * requests for objects this plugin itself uploaded in parts, and buy nothing in return: unlike
		 * an upload or a copy, a single-request download of a large object is never rejected.
		 */
		static MultipartConfiguration downloadMultipartConfiguration() {
			return null;
		}

		public S3AsyncClientBuilder createS3AsyncClientBuilderForDownload() {
			return this.createS3AsyncClientBuilder(downloadMultipartConfiguration());
		}

		/**
		 * A null configuration means "no multipart", which is a download.
		 */
		private S3AsyncClientBuilder createS3AsyncClientBuilder(MultipartConfiguration multipartConfiguration) {
			S3AsyncClientBuilder builder = this.applyTo(S3AsyncClient.builder());
			if (multipartConfiguration == null) {
				return builder;
			}
			return builder
					.multipartEnabled(true)
					.multipartConfiguration(multipartConfiguration);
		}

		/**
		 * v1 had pathStyleAccessEnabled and payloadSigningEnabled directly on the client builder;
		 * v2 moves both under S3Configuration.
		 *
		 * pathStyleAccessEnabled maps across unchanged. payloadSigningEnabled has no exact v2
		 * equivalent: v1 used it to sign the request body in one piece instead of relying on
		 * aws-chunked signing, so the closest v2 expression is to turn chunked encoding off. Left
		 * at the SDK default when the option is off, which is v1's default too.
		 */
		public S3Configuration createS3Configuration() {
			S3Configuration.Builder configuration = S3Configuration.builder()
					.pathStyleAccessEnabled(this.isPathStyleAccessEnabled());
			if (this.isPayloadSigningEnabled()) {
				configuration.chunkedEncodingEnabled(false);
			}
			return configuration.build();
		}

		private <B extends S3BaseClientBuilder<B, ?>> B applyTo(B builder) {
			return builder.serviceConfiguration(this.createS3Configuration());
		}
	}

}
