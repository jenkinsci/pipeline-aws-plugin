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

import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundSetter;

import com.amazonaws.services.s3.AmazonS3ClientBuilder;

public class AbstractS3Step extends AbstractStepImpl {
	
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
		
		protected AmazonS3ClientBuilder createAmazonS3ClientBuilder() {
			return AmazonS3ClientBuilder.standard()
					.withPathStyleAccessEnabled(this.isPathStyleAccessEnabled())
					.withPayloadSigningEnabled(this.isPayloadSigningEnabled());
		}
	}
	
}
