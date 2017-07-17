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
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.base.Preconditions;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

/**
 * The S3FindFilesStep returns a list of files from S3 that match a pattern.
 * This is intended to be analogous to the "findFiles" step provided by the
 * "pipeline-utility-steps-plugin".
 *
 * This thus accepts a bucket, a path, and a glob pattern.
 *
 * The path, if specified, sets the root of the search.  If left unspecified, then
 * this defaults to the root of the bucket.
 *
 * The glob, if specified, sets the glob that should be matched.  If left unspecified,
 * then this defaults to "*", which will match everything within `path`, but only
 * one level deep.  To match absolutely everything, use "**".
 */
public class S3FindFilesStep extends AbstractStepImpl {
	/**
	 * This is the bucket name.
	 */
	private final String bucket;
	/**
	 * This is the path to limit the search to.
	 * This defaults to the empty string, which is the root of the bucket.
	 */
	private String path = "";
	/**
	 * This is the glob.
	 * This defaults to the empty string, which lists the files directly under the path.
	 */
	private String glob = "";
	/**
	 * This is whether or not we are only going to return files.
	 * By default, both files and folders are returned.
	 */
	private boolean onlyFiles = false;

	@DataBoundConstructor
	public S3FindFilesStep(String bucket) {
		this.bucket = bucket;
	}

	public String getBucket() {
		return this.bucket;
	}

	@DataBoundSetter
	public void setPath( String path ) {
		this.path = path;
	}

	public String getPath() {
		return this.path;
	}

	@DataBoundSetter
	public void setGlob( String glob ) {
		this.glob = glob;
	}

	public String getGlob() {
		return this.glob;
	}

	@DataBoundSetter
	public void setOnlyFiles( boolean onlyFiles ) {
		this.onlyFiles = onlyFiles;
	}

	public boolean isOnlyFiles() {
		return this.onlyFiles;
	}

	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {

		public DescriptorImpl() {
			super(Execution.class);
		}

		@Override
		public String getFunctionName() {
			return "s3FindFiles";
		}

		@Override
		public String getDisplayName() {
			return "Find files in S3";
		}
	}

	public static class Execution extends AbstractSynchronousNonBlockingStepExecution<FileWrapper[]> {
		private static final long serialVersionUID = 1L;

		@Inject
		private transient S3FindFilesStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient FilePath workspace;
		@StepContextParameter
		private transient TaskListener listener;

		@Override
		public FileWrapper[] run() throws Exception {
			final String bucket = this.step.getBucket();
			final String path = this.step.getPath();
			final String glob = this.step.getGlob();
			final boolean onlyFiles = this.step.isOnlyFiles();

			Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "Bucket must not be null or empty");

			try {
				Execution.this.listener.getLogger().format("Searching s3://%s/%s for glob:'%s' %s%n", bucket, path, glob, onlyFiles ? "(only files)" : "" );

				AmazonS3Client s3Client = AWSClientFactory.create(AmazonS3Client.class, Execution.this.envVars);

				// Construct a PatternMatcher to match the files.
				// Essentially, we're going to match against "${path}/${glob}".  Obviously,
				// if there's no path, then we're going to leave that part out.  If no glob
				// was given, then we're going to default to "*", which will match everything
				// at the level of the path (but no deeper).
				//
				// Note that a glob of "**" will match everything (both files and folders) under
				// the path.
				final String matcherString = computeMatcherString( path, glob );
				PathMatcher matcher = FileSystems.getDefault().getPathMatcher( matcherString );

				// This is how may components there are in the root path.  We'll use this information
				// to strip out these parts from the matches later on.
				//
				// For exmple, if `path` is "path/to", then this will be "2".
				final int pathComponentCount = path.length() == 0 ? 0 : Paths.get( path ).getNameCount();

				// This is the list of S3 file information for all of the matching objects.
				List<FileWrapper> matchingObjects = new ArrayList<>();

				// This is the list of folders that we need to investigate.
				// We're going to start with the path that we've been given,
				// and then we'll grow it from there.
				List<String> folders = new ArrayList<>();
				folders.add( path );

				// Go through all of the folders that we need to investigate,
				// popping the first item off and working on it.  When they're
				// all gone, we'll be done.
				while( folders.size() > 0 ) {
					// This is the folder to investigate.
					String folder = folders.remove( 0 );

					// Create the request to list the objects within it.
					ListObjectsRequest request = new ListObjectsRequest();
					request.setBucketName( bucket );
					request.setPrefix( folder );
					request.setDelimiter( "/" );
					if( folder.length() > 0 && ! folder.endsWith("/") ) {
						request.setPrefix( folder + "/" );
					}

					// Get the list of objects within the folder.  Because AWS
					// might paginate this, we're going to continue dealing with
					// the "objectListing" object until it claims that it's done.
					ObjectListing objectListing = s3Client.listObjects(request);
					while( true ) {
						// Add any real objects to the list of objects to delete.
						for( S3ObjectSummary entry : objectListing.getObjectSummaries() ) {
							// S3 does this sneaky thing with folders created in the management console:
							// It *actually* creates a zero-length file whose name ends in "/".
							//
							// Here, we're going to quietly skip those entries; they'll be handled normally
							// by the folder pathway below, anyway.  (Yes, they are returned as actual s3
							// entities as well as prefixes).
							if( entry.getKey().endsWith("/") ) {
								continue;
							}

							Path javaPath = Paths.get(entry.getKey());
							if( matcher.matches( javaPath ) ) {
								FileWrapper file = createFileWrapperFromFile( pathComponentCount, javaPath, entry );
								matchingObjects.add( file );
							}
						}
						// Add any folders to the list of folders that we need to investigate.
						folders.addAll( objectListing.getCommonPrefixes() );
						// In addition, if we are allowed to add folders to the list, then
						// go through the folders and add any matching ones.
						if( ! onlyFiles ) {
							for( String prefix : objectListing.getCommonPrefixes() ) {
								Path javaPath = Paths.get(prefix);
								if( matcher.matches( javaPath ) ) {
									FileWrapper file = createFileWrapperFromFolder( pathComponentCount, javaPath );
									matchingObjects.add( file );
								}
							}
						}

						// If this listing is complete, then we can stop.
						if( ! objectListing.isTruncated() ) {
							break;
						}
						// Otherwise, we need to get the next batch and repeat.
						objectListing = s3Client.listNextBatchOfObjects(objectListing);
					}
				}

				FileWrapper[] stepResult = new FileWrapper[ matchingObjects.size() ];
				stepResult = matchingObjects.toArray( stepResult );

				Execution.this.listener.getLogger().println("Search complete");
				return stepResult;
			} catch (Exception e) {
				Execution.this.getContext().onFailure(e);
				return null;
			}
		}

		/**
		 * This computes the string that will be used to construct a PathMatcher that will
		 * attempt to match the S3 keys.
		 *
		 * @param path The step's `path` parameter.
		 * @param glob The step's `glob` parameter.
		 *
		 * @return A string that can be used to construct a PathMatcher.
		 */
		public static String computeMatcherString( String path, String glob ) {
			return "glob:" + ( path.length() == 0 ? "" : path + ( path.endsWith("/") ? "" : "/" ) ) + ( glob.length() == 0 ? "*" : glob );
		}

		/**
		 * This creates a new FileWrapper instance based on the S3ObjectSummary information.
		 *
		 * @param pathComponentCount The root path component count.
		 * @param javaPath The Path instance for the file.
		 * @param entry The S3ObjectSummary for the file.
		 *
		 * @return A new FileWrapper instance.
		 */
		public static FileWrapper createFileWrapperFromFile( int pathComponentCount, Path javaPath, S3ObjectSummary entry ) {
			FileWrapper file = new FileWrapper(
				// Name:
				javaPath.getFileName().toString(),
				// Path (relative to the `path` parameter):
				javaPath.subpath( pathComponentCount, javaPath.getNameCount() ).toString(),
				// Directory?
				false,
				// Size:
				entry.getSize(),
				// Last modified (milliseconds):
				entry.getLastModified().getTime()
			);
			return file;
		}

		/**
		 * This creates a new FileWrapper instance for the folder.
		 *
		 * @param pathComponentCount The root path component count.
		 * @param javaPath The Path instance for the folder.
		 *
		 * @return A new FileWrapper instance.
		 */
		public static FileWrapper createFileWrapperFromFolder( int pathComponentCount, Path javaPath ) {
			FileWrapper file = new FileWrapper(
				// Name:
				javaPath.getFileName().toString(),
				// Path (relative to the `path` parameter):
				javaPath.subpath( pathComponentCount, javaPath.getNameCount() ).toString(),
				// Directory?
				true,
				// Size:
				0, // S3 folders have no size (they don't even really exist).
				// Last modified (milliseconds):
				0 // S3 folders have no last modified date (they don't even really exist).
			);
			return file;
		}
	}
}
