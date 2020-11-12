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

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
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
import org.joda.time.DateTime;
import org.kohsuke.stapler.DataBoundConstructor;

import java.net.URL;
import java.util.Date;
import java.util.Set;

public class S3PresignUrlStep extends AbstractS3Step {

	private final String bucket;
	private final String key;
	private final int durationInSeconds;
	private final HttpMethod httpMethod;

	@DataBoundConstructor
	public S3PresignUrlStep(String bucket, String key, String httpMethod, Integer durationInSeconds, boolean pathStyleAccessEnabled, boolean payloadSigningEnabled) {
		super(pathStyleAccessEnabled, payloadSigningEnabled);
		this.bucket = bucket;
		this.key = key;
		if (durationInSeconds == null) {
			this.durationInSeconds = 60; //60 seconds
		} else {
			this.durationInSeconds = durationInSeconds;
		}
		if (httpMethod == null) {
			this.httpMethod = HttpMethod.GET;
		} else {
			this.httpMethod = HttpMethod.valueOf(httpMethod);
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

	public HttpMethod getHttpMethod() {
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
			AmazonS3 s3 = AWSClientFactory.create(this.step.createS3ClientOptions().createAmazonS3ClientBuilder(), this.getContext(), envVars);
			Date expiration = DateTime.now().plusSeconds(this.step.getDurationInSeconds()).toDate();
			URL url = s3.generatePresignedUrl(bucket, key, expiration, this.step.getHttpMethod());
			return url.toString();
		}

	}
}
