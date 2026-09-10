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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;

import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import com.google.common.base.Preconditions;

import de.taimos.pipeline.aws.utils.S3Utils;
import de.taimos.pipeline.aws.utils.StepUtils;
import hudson.Extension;
import hudson.model.TaskListener;

/**
 * The S3DeleteStep deletes an object from S3.
 * If the path ends with a "/", then this interprets the object as a folder
 * and removes all content, as well.
 */
public class S3DeleteStep extends AbstractS3Step {
	/**
	 * This is the bucket name.
	 */
	private final String bucket;
	/**
	 * This is the path to the object.
	 */
	private final String path;

	@DataBoundConstructor
	public S3DeleteStep(String bucket, String path, boolean pathStyleAccessEnabled, boolean payloadSigningEnabled) {
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
		return new S3DeleteStep.Execution(this, context);
	}

	@Extension
	public static class DescriptorImpl extends StepDescriptor {

		@Override
		public Set<? extends Class<?>> getRequiredContext() {
			return StepUtils.requiresDefault();
		}

		@Override
		public String getFunctionName() {
			return "s3Delete";
		}

		@Override
		public String getDisplayName() {
			return "Delete file from S3";
		}
	}

	public static class Execution extends StepExecution {

		protected static final long serialVersionUID = 1L;

		protected transient S3DeleteStep step;

		public Execution(S3DeleteStep step, StepContext context) {
			super(context);
			this.step = step;
		}

		@Override
		public boolean start() throws Exception {
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");

			new Thread("s3Delete") {
				@Override
				public void run() {
					try {
						TaskListener listener = Execution.this.getContext().get(TaskListener.class);
						listener.getLogger().format("Deleting s3://%s/%s%n", bucket, path);
						S3Client s3Client = AWSClientFactory.create(Execution.this.step.createS3ClientOptions().createS3ClientBuilder(), Execution.this.getContext());

						if (path != null && !path.endsWith("/") && !path.isEmpty()) {
							this.deleteFile(s3Client);
						} else {
							this.deleteFolder(s3Client);
						}

						listener.getLogger().println("Delete complete");
						Execution.this.getContext().onSuccess(null);
					} catch (Exception e) {
						Execution.this.getContext().onFailure(e);
					}
				}

				private void deleteFolder(S3Client s3Client) throws IOException, InterruptedException {
					// This is the list of keys to delete from the bucket.
					List<String> objectsToDelete = new ArrayList<>();

					// See if the thing that we were given is a file.
					if (!path.isEmpty() && S3Utils.doesObjectExist(s3Client, bucket, path)) {
						objectsToDelete.add(path);
					}

					this.searchObjectsRecursively(s3Client, objectsToDelete);

					// Go through all of the objects that we want to delete and actually delete them.
					for (String objectToDelete : objectsToDelete) {
						Execution.this.getContext().get(TaskListener.class).getLogger().format("Deleting object at s3://%s/%s%n", bucket, objectToDelete);
						// TODO Use deleteObjects to reduce API calls
						s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectToDelete).build());
					}
				}

				private void searchObjectsRecursively(S3Client s3Client, List<String> objectsToDelete) {
					// This is the list of folders that we need to investigate.
					// We're going to start with the path that we've been given,
					// and then we'll grow it from there.
					List<String> folders = new ArrayList<>();
					folders.add(path);

					// Go through all of the folders that we need to investigate,
					// popping the first item off and working on it.  When they're
					// all gone, we'll be done.
					while (!folders.isEmpty()) {
						// This is the folder to investigate.
						String folder = folders.remove(0);

						// Create the request to list the objects within it.
						ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
								.bucket(bucket)
								.delimiter("/");

						if (folder.equals("/") || folder.isEmpty()) {
							request.prefix(null);
						} else if (!folder.endsWith("/")) {
							request.prefix(folder + "/");
						} else {
							request.prefix(folder);
						}

						// The paginator issues the same sequence of calls the hand-rolled
						// listNextBatchOfObjects loop did.
						for (ListObjectsV2Response page : s3Client.listObjectsV2Paginator(request.build())) {
							// Add any real objects to the list of objects to delete.
							for (S3Object entry : page.contents()) {
								objectsToDelete.add(entry.key());
							}
							// Add any folders to the list of folders that we need to investigate.
							for (CommonPrefix commonPrefix : page.commonPrefixes()) {
								folders.add(commonPrefix.prefix());
							}
						}
					}
				}

				private void deleteFile(S3Client s3Client) throws IOException, InterruptedException {
					// See if the thing that we were given is a file.
					if (S3Utils.doesObjectExist(s3Client, bucket, path)) {
						Execution.this.getContext().get(TaskListener.class).getLogger().format("Deleting object at s3://%s/%s%n", bucket, path);
						s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(path).build());
					}
				}
			}.start();
			return false;
		}

		@Override
		public void stop(@NonNull Throwable cause) throws Exception {
			//
		}

	}
}
