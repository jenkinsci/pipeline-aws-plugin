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
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AbstractS3Step extends AbstractStepImpl {

	boolean pathStyleAccessEnabled = false;
	boolean payloadSigningEnabled = false;

	public boolean isPathStyleAccessEnabled() {
		return pathStyleAccessEnabled;
	}

	@DataBoundSetter
	public void setPathStyleAccessEnabled(final boolean pathStyleAccessEnabled) {
		this.pathStyleAccessEnabled = pathStyleAccessEnabled;
	}

	public boolean isPayloadSigningEnabled() {
		return payloadSigningEnabled;
	}

	@DataBoundSetter
	public void setPayloadSigningEnabled(final boolean payloadSigningEnabled) {
		this.payloadSigningEnabled = payloadSigningEnabled;
	}

	protected AmazonS3ClientBuilder createAmazonS3ClientBuilder() {
		final boolean pathStyleAccessEnabled = this.isPathStyleAccessEnabled();
		final boolean payloadSigningEnabled = this.isPayloadSigningEnabled();
		return AmazonS3ClientBuilder.standard()
				.withPathStyleAccessEnabled(pathStyleAccessEnabled)
				.withPayloadSigningEnabled(payloadSigningEnabled);

	}

	public abstract static class AbstractS3StepExecution<S extends AbstractS3Step> extends AbstractStepExecutionImpl {

		protected static final long serialVersionUID = 1L;
		@Inject
		protected transient S step;
		@StepContextParameter
		protected transient EnvVars envVars;
		@StepContextParameter
		protected transient FilePath workspace;
		@StepContextParameter
		protected transient TaskListener listener;

	}
}
