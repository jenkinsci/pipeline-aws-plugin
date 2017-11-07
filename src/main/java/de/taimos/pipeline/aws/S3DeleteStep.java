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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.base.Preconditions;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
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
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
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
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		protected static final long serialVersionUID = 1L;
		@Inject
		protected transient S3DeleteStep step;
		@StepContextParameter
		protected transient EnvVars envVars;
		@StepContextParameter
		protected transient FilePath workspace;
		@StepContextParameter
		protected transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			
			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			
			new Thread("s3Delete") {
				@Override
				public void run() {
					try {
						Execution.this.listener.getLogger().format("Deleting s3://%s/%s%n", bucket, path);
						AmazonS3 s3Client = AWSClientFactory.create(Execution.this.step.createAmazonS3ClientBuilder(), Execution.this.envVars);
						
						if (!path.endsWith("/")) {
							// See if the thing that we were given is a file.
							if (s3Client.doesObjectExist(bucket, path)) {
								Execution.this.listener.getLogger().format("Deleting object at s3://%s/%s%n", bucket, path);
								s3Client.deleteObject(bucket, path);
							}
						} else {
							// This is the list of keys to delete from the bucket.
							List<String> objectsToDelete = new ArrayList<>();
							
							// See if the thing that we were given is a file.
							if (s3Client.doesObjectExist(bucket, path)) {
								objectsToDelete.add(path);
							}
							
							// This is the list of folders that we need to investigate.
							// We're going to start with the path that we've been given,
							// and then we'll grow it from there.
							List<String> folders = new ArrayList<>();
							folders.add(path);
							
							// Go through all of the folders that we need to investigate,
							// popping the first item off and working on it.  When they're
							// all gone, we'll be done.
							while (folders.size() > 0) {
								// This is the folder to investigate.
								String folder = folders.remove(0);
								
								// Create the request to list the objects within it.
								ListObjectsRequest request = new ListObjectsRequest();
								request.setBucketName(bucket);
								request.setPrefix(folder);
								request.setDelimiter("/");
								if (!folder.endsWith("/")) {
									request.setPrefix(folder + "/");
								}
								
								// Get the list of objects within the folder.  Because AWS
								// might paginate this, we're going to continue dealing with
								// the "objectListing" object until it claims that it's done.
								ObjectListing objectListing = s3Client.listObjects(request);
								while (true) {
									// Add any real objects to the list of objects to delete.
									for (S3ObjectSummary entry : objectListing.getObjectSummaries()) {
										objectsToDelete.add(entry.getKey());
									}
									// Add any folders to the list of folders that we need to investigate.
									folders.addAll(objectListing.getCommonPrefixes());
									
									// If this listing is complete, then we can stop.
									if (!objectListing.isTruncated()) {
										break;
									}
									// Otherwise, we need to get the next batch and repeat.
									objectListing = s3Client.listNextBatchOfObjects(objectListing);
								}
							}
							
							// Go through all of the objects that we want to delete and actually delete them.
							for (String objectToDelete : objectsToDelete) {
								Execution.this.listener.getLogger().format("Deleting object at s3://%s/%s%n", bucket, objectToDelete);
								s3Client.deleteObject(bucket, objectToDelete);
							}
						}
						
						Execution.this.listener.getLogger().println("Delete complete");
						Execution.this.getContext().onSuccess(null);
					} catch (RuntimeException e) {
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
