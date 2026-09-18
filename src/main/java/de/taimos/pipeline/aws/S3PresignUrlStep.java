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

import com.google.common.base.Preconditions;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class S3PresignUrlStep extends AbstractS3Step {

	/**
	 * v1's generatePresignedUrl took an HttpMethod and signed a URL for it, so it accepted anything
	 * in com.amazonaws.HttpMethod - including POST and PATCH. v2 presigns per operation instead, and
	 * offers only these four. POST and PATCH are rejected in the constructor rather than silently
	 * producing a GET URL.
	 */
	private static final List<String> SUPPORTED_HTTP_METHODS = Arrays.asList("GET", "PUT", "DELETE", "HEAD");

	/**
	 * SigV4 query-parameter presigning is valid for at least one second and at most seven days, and
	 * v2's signer throws IllegalArgumentException outside that range. v1 signed an over-long expiry
	 * and left S3 to reject the resulting URL, so this is validated up front to name the parameter
	 * rather than surface an SDK message from inside the step.
	 */
	private static final int MAX_DURATION_IN_SECONDS = 604800;

	private final String bucket;
	private final String key;
	private final int durationInSeconds;
	private final String httpMethod;

	@DataBoundConstructor
	public S3PresignUrlStep(String bucket, String key, String httpMethod, Integer durationInSeconds, boolean pathStyleAccessEnabled, boolean payloadSigningEnabled) {
		super(pathStyleAccessEnabled, payloadSigningEnabled);
		this.bucket = bucket;
		this.key = key;
		if (durationInSeconds == null) {
			this.durationInSeconds = 60; //60 seconds
		} else {
			Preconditions.checkArgument(durationInSeconds >= 1 && durationInSeconds <= MAX_DURATION_IN_SECONDS,
					"durationInSeconds must be between 1 and %s (7 days), got %s", MAX_DURATION_IN_SECONDS, durationInSeconds);
			this.durationInSeconds = durationInSeconds;
		}
		if (httpMethod == null) {
			this.httpMethod = "GET";
		} else {
			this.httpMethod = httpMethod.toUpperCase(Locale.ROOT);
			Preconditions.checkArgument(SUPPORTED_HTTP_METHODS.contains(this.httpMethod),
					"httpMethod must be one of %s, got '%s'", SUPPORTED_HTTP_METHODS, httpMethod);
		}
	}

	public String getBucket() {
		return bucket;
	}

	public String getKey() {
		return key;
	}

	public int getDurationInSeconds() {
		return durationInSeconds;
	}

	public String getHttpMethod() {
		return httpMethod;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new S3PresignUrlStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
		}

		@Override
		public String getFunctionName() {
			return "s3PresignURL";
		}

		@Override
		public String getDisplayName() {
			return "Presign file in S3";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution {

		protected static final long serialVersionUID = 1L;

		protected final transient S3PresignUrlStep step;

		public Execution(S3PresignUrlStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		protected Object run() throws Exception {
			final String bucket = this.step.getBucket();
			final String key = this.step.getKey();

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			Preconditions.checkArgument(key != null && !key.isEmpty(), "Key must not be null or empty");

			EnvVars envVars = this.getContext().get(EnvVars.class);
			Duration signatureDuration = Duration.ofSeconds(this.step.getDurationInSeconds());

			try (S3Presigner presigner = AWSClientFactory.createS3Presigner(
					this.step.createS3ClientOptions().createS3Configuration(), this.getContext(), envVars)) {
				URL url = presign(presigner, this.step.getHttpMethod(), bucket, key, signatureDuration);
				return url.toString();
			}
		}

		private static URL presign(S3Presigner presigner, String httpMethod, String bucket, String key, Duration signatureDuration) {
			switch (httpMethod) {
				case "PUT":
					return presigner.presignPutObject(r -> r
							.signatureDuration(signatureDuration)
							.putObjectRequest(PutObjectRequest.builder().bucket(bucket).key(key).build())).url();
				case "DELETE":
					return presigner.presignDeleteObject(r -> r
							.signatureDuration(signatureDuration)
							.deleteObjectRequest(DeleteObjectRequest.builder().bucket(bucket).key(key).build())).url();
				case "HEAD":
					return presigner.presignHeadObject(r -> r
							.signatureDuration(signatureDuration)
							.headObjectRequest(HeadObjectRequest.builder().bucket(bucket).key(key).build())).url();
				case "GET":
					return presigner.presignGetObject(r -> r
							.signatureDuration(signatureDuration)
							.getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())).url();
				default:
					// Unreachable while the constructor validates against SUPPORTED_HTTP_METHODS, but
					// the two lists are independent: adding a verb there and not here would otherwise
					// silently presign a GET, which is what the constructor check exists to prevent.
					throw new IllegalStateException("Unsupported httpMethod: " + httpMethod);
			}
		}

	}
}
