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

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.ObjectMetadataProvider;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.base.Preconditions;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class S3UploadKMSStep extends AbstractStepImpl {

	private final String file;
	private final String bucket;
	private final String regionName;
	private String path = "";

	@DataBoundConstructor
	public S3UploadKMSStep(String file, String bucket, String regionName) {
		this.file = file;
		this.bucket = bucket;
		this.regionName = regionName;
	}
	
	public String getFile() {
		return this.file;
	}
	
	public String getBucket() {
		return this.bucket;
	}
	
	public String getPath() {
		return this.path;
	}

	public String getRegionName() {
		return this.regionName;
	}

	@DataBoundSetter
	public void setPath(String path) {
		this.path = path;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "s3UploadKMS";
		}
		
		@Override
		public String getDisplayName() {
			return "Copy file to S3";
		}
	}
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient S3UploadKMSStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final FilePath child = this.workspace.child(this.step.getFile());
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final String regionName = this.step.getRegionName();
			
			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			
			new Thread("s3UploadKMS") {
				@Override
				public void run() {
					try {
						Execution.this.listener.getLogger().format("Uploading %s to s3://%s/%s %n", child.toURI(), bucket, path);
						if (!child.exists()) {
							Execution.this.listener.getLogger().println("Upload failed due to missing source file");
							Execution.this.getContext().onFailure(new FileNotFoundException(child.toURI().toString()));
							return;
						}
						
						child.act(new RemoteUploader(Execution.this.envVars, Execution.this.listener, bucket, path, regionName));
						
						Execution.this.listener.getLogger().println("Upload complete");
						Execution.this.getContext().onSuccess(null);
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
		
		private static final long serialVersionUID = 1L;
		
	}
	
	private static class RemoteUploader implements FilePath.FileCallable<Void> {
		
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private final String regionName;
		
		RemoteUploader(EnvVars envVars, TaskListener taskListener, String bucket, String path, String regionName) {
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.bucket = bucket;
			this.path = path;
			this.regionName = regionName;
		}
		
		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			AmazonS3Client s3Client = AWSClientFactory.create(AmazonS3Client.class, this.envVars);
			if(null != this.regionName && !this.regionName.isEmpty()) {
				s3Client.setRegion(Region.getRegion(Regions.fromName(this.regionName)));
			}
			TransferManager mgr = new TransferManager(s3Client);

			if (localFile.isFile()) {
				InputStream inputStream = new FileInputStream(localFile);
				try {
					Preconditions.checkArgument(path != null && !path.isEmpty(), "Path must not be null or empty when uploading file");
					ObjectMetadata objectMetaData = new ObjectMetadata();
					objectMetaData.setSSEAlgorithm("aws:kms");
					final Upload upload = mgr.upload(this.bucket, this.path, inputStream, objectMetaData);
					upload.addProgressListener(new ProgressListener() {
						@Override
						public void progressChanged(ProgressEvent progressEvent) {
							if (progressEvent.getEventType() == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
								S3UploadKMSStep.RemoteUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
							}
						}
					});
					upload.waitForCompletion();
					return null;
				}finally {
					inputStream.close();
				}
			}
			if (localFile.isDirectory()) {
				ObjectMetadataProvider provider = new ObjectMetadataProvider(){

					@Override
					public void provideObjectMetadata(File file, ObjectMetadata metadata) {
						metadata.setSSEAlgorithm("aws:kms");
					}} ;
				final MultipleFileUpload fileUpload = mgr.uploadDirectory(this.bucket, this.path, localFile, true, provider);
				for (final Upload upload : fileUpload.getSubTransfers()) {
					upload.addProgressListener(new ProgressListener() {
						@Override
						public void progressChanged(ProgressEvent progressEvent) {
							if (progressEvent.getEventType()== ProgressEventType.TRANSFER_COMPLETED_EVENT) {
								S3UploadKMSStep.RemoteUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
							}
						}
					});
				}
				fileUpload.waitForCompletion();
				return null;
			}
			return null;
		}
		
		@Override
		public void checkRoles(RoleChecker roleChecker) throws SecurityException {
			
		}
	}
	
}
