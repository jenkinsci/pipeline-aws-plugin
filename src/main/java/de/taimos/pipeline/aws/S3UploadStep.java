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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.MultipleFileUpload;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.common.base.Preconditions;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public class S3UploadStep extends AbstractStepImpl {
	
	private String file;
	private final String bucket;
	private String path = "";
	private String includePathPattern;
	private String excludePathPattern;
	private String workingDir;
	
	@DataBoundConstructor
	public S3UploadStep(String bucket) {
		this.bucket = bucket;
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
	
	public String getIncludePathPattern() {
		return this.includePathPattern;
	}
	
	public String getExcludePathPattern() {
		return this.excludePathPattern;
	}
	
	public String getWorkingDir() {
		return this.workingDir;
	}
	
	@DataBoundSetter
	public void setFile(String file) {
		this.file = file;
	}
	
	@DataBoundSetter
	public void setPath(String path) {
		this.path = path;
	}
	
	@DataBoundSetter
	public void setIncludePathPattern(String includePathPattern) {
		this.includePathPattern = includePathPattern;
	}
	
	@DataBoundSetter
	public void setExcludePathPattern(String excludePathPattern) {
		this.excludePathPattern = excludePathPattern;
	}
	
	@DataBoundSetter
	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(Execution.class);
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
	
	public static class Execution extends AbstractStepExecutionImpl {
		
		@Inject
		private transient S3UploadStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		public boolean start() throws Exception {
			final String file = this.step.getFile();
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final String includePathPattern = this.step.getIncludePathPattern();
			final String excludePathPattern = this.step.getExcludePathPattern();
			final String workingDir = this.step.getWorkingDir();
						
			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");
			Preconditions.checkArgument(file != null || includePathPattern != null, "File or IncludePathPattern must not be null");
			Preconditions.checkArgument(includePathPattern == null || file == null, "File and IncludePathPattern cannot be use together");
			
			final List<FilePath> childs = new ArrayList<FilePath>();
			final FilePath dir;
			if( workingDir != null && !"".equals(workingDir.trim()) ) {
				dir = this.workspace.child(workingDir);
			} else {
				dir = this.workspace;
			}
			if(file != null) {
				childs.add(dir.child(file));
			} else if (includePathPattern != null){
				if(excludePathPattern != null && !"".equals(excludePathPattern.trim())){
					childs.addAll(Arrays.asList(dir.list(includePathPattern, excludePathPattern, true)));
				} else {
					childs.addAll(Arrays.asList(dir.list(includePathPattern, null, true)));
				}
			}
			
			new Thread("s3Upload") {
				@Override
				public void run() {
					try {
						if( childs != null && childs.size() == 1) {
							FilePath child = childs.get(0);
							Execution.this.listener.getLogger().format("Uploading %s to s3://%s/%s %n", child.toURI(), bucket, path);
							if (!child.exists()) {
								Execution.this.listener.getLogger().println("Upload failed due to missing source file");
								Execution.this.getContext().onFailure(new FileNotFoundException(child.toURI().toString()));
								return;
							}
							
							child.act(new RemoteUploader(Execution.this.envVars, Execution.this.listener, bucket, path));
							
							Execution.this.listener.getLogger().println("Upload complete");
							Execution.this.getContext().onSuccess(null);
						} else if( childs != null && childs.size() > 1) {
							List<File> fileList = new ArrayList<File>();
							Execution.this.listener.getLogger().format("Uploading %s to s3://%s/%s %n", includePathPattern, bucket, path);
							for( FilePath child : childs ) {
								child.act(new FeedList(fileList));
							}
							dir.act(new RemoteListUploader(Execution.this.envVars, Execution.this.listener, fileList, bucket, path));
							Execution.this.listener.getLogger().println("Upload complete");
							Execution.this.getContext().onSuccess(null);
						}
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
		
		RemoteUploader(EnvVars envVars, TaskListener taskListener, String bucket, String path) {
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.bucket = bucket;
			this.path = path;
		}
		
		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			AmazonS3Client s3Client = AWSClientFactory.create(AmazonS3Client.class, this.envVars);
			TransferManager mgr = TransferManagerBuilder.standard().withS3Client(s3Client).build();
			if (localFile.isFile()) {
				Preconditions.checkArgument(this.path != null && !this.path.isEmpty(), "Path must not be null or empty when uploading file");
				final Upload upload = mgr.upload(this.bucket, this.path, localFile);
				upload.addProgressListener(new ProgressListener() {
					@Override
					public void progressChanged(ProgressEvent progressEvent) {
						if (progressEvent.getEventType()== ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							RemoteUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
						}
					}
				});
				upload.waitForCompletion();
				return null;
			}
			if (localFile.isDirectory()) {
				final MultipleFileUpload fileUpload = mgr.uploadDirectory(this.bucket, this.path, localFile, true);
				for (final Upload upload : fileUpload.getSubTransfers()) {
					upload.addProgressListener(new ProgressListener() {
						@Override
						public void progressChanged(ProgressEvent progressEvent) {
							if (progressEvent.getEventType()== ProgressEventType.TRANSFER_COMPLETED_EVENT) {
								RemoteUploader.this.taskListener.getLogger().println("Finished: " + upload.getDescription());
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
	
	private static class RemoteListUploader implements FilePath.FileCallable<Void> {
		
		private final EnvVars envVars;
		private final TaskListener taskListener;
		private final String bucket;
		private final String path;
		private List<File> fileList;
		
		RemoteListUploader(EnvVars envVars, TaskListener taskListener, List<File> fileList, String bucket, String path) {
			this.envVars = envVars;
			this.taskListener = taskListener;
			this.fileList = fileList;
			this.bucket = bucket;
			this.path = path;
		}
		
		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			AmazonS3Client s3Client = AWSClientFactory.create(AmazonS3Client.class, this.envVars);
			TransferManager mgr = TransferManagerBuilder.standard().withS3Client(s3Client).build();
			Preconditions.checkArgument(path != null && !path.isEmpty(), "Path must not be null or empty when uploading file");
			final MultipleFileUpload fileUpload = mgr.uploadFileList(bucket, path, localFile, fileList);
			for (final Upload upload : fileUpload.getSubTransfers()) {
				upload.addProgressListener(new ProgressListener() {
					@Override
					public void progressChanged(ProgressEvent progressEvent) {
						if (progressEvent.getEventType()== ProgressEventType.TRANSFER_COMPLETED_EVENT) {
							taskListener.getLogger().println("Finished: " + upload.getDescription());
						}
					}
				});
			}
			fileUpload.waitForCompletion();
			return null;
		}
		
		@Override
		public void checkRoles(RoleChecker roleChecker) throws SecurityException {
		}
	}
	
	private static class FeedList implements FilePath.FileCallable<Void> {
		
		private final List<File> fileList;
		
		FeedList(List<File> fileList) {
			this.fileList = fileList;
		}
		
		@Override
		public Void invoke(File localFile, VirtualChannel channel) throws IOException, InterruptedException {
			fileList.add(localFile);
			return null;
		}

		@Override
		public void checkRoles(RoleChecker arg0) throws SecurityException {
		}
	}
	
}
	