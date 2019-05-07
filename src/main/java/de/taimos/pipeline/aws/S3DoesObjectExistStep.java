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
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;

/**
 * The S3DoesObjectExistStep returns a boolean signalling whether an object exists in S3 or not.
 * <p>
 * This thus accepts a bucket and a path.
 */
public class S3DoesObjectExistStep extends AbstractS3Step {
	/**
	 * This is the bucket name.
	 */
	private final String bucket;
	/**
	 * This is the path to limit the search to.
	 */
	private final String path;

	@DataBoundConstructor
	public S3DoesObjectExistStep(String bucket, String path, boolean pathStyleAccessEnabled, boolean payloadSigningEnabled) {
		super(pathStyleAccessEnabled, payloadSigningEnabled);
		this.bucket = bucket;
		this.path = path;
	}

	public String getBucket() {
		return this.bucket;
	}

	public String getPath() {
		return this.path;
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
		return new S3DoesObjectExistStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class);
		}

		@Override
		public String getFunctionName() {
			return "s3DoesObjectExist";
		}

		@Override
		public String getDisplayName() {
			return "Check if object exists in S3";
		}
	}

	public static class Execution extends SynchronousNonBlockingStepExecution<Boolean> {
		private static final long serialVersionUID = 1L;

		private final transient S3DoesObjectExistStep step;

		public Execution(S3DoesObjectExistStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public Boolean run() throws Exception {
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			Preconditions.checkArgument(path != null && !path.isEmpty(), "Path must not be null or empty");

			this.getContext().get(TaskListener.class).getLogger().format("Searching s3://%s for object:'%s'%n", bucket, path);

			AmazonS3 s3Client = AWSClientFactory.create(Execution.this.step.createS3ClientOptions().createAmazonS3ClientBuilder(), Execution.this.getContext());

			Boolean stepResult = s3Client.doesObjectExist(bucket, path);

			this.getContext().get(TaskListener.class).getLogger().println("Search complete");
			return stepResult;
		}
	}
}
