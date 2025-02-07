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

import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;

import jakarta.annotation.Nonnull;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.StaplerRequest2;

/**
* Global configuration
*/
@Extension
@Symbol("pipelineStepsAWS")
public class PluginImpl extends GlobalConfiguration {

	private boolean enableCredentialsFromNode;

	/**
	 * Default constructor.
	 */
	@DataBoundConstructor
	public PluginImpl() {
		load();
	}

	@Override
	public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
		json = json.getJSONObject("enableCredentialsFromNode");
		enableCredentialsFromNode = json.getBoolean("enableCredentialsFromNode");
		save();
		return true;
	}

	/**
	 * Whether or not to retrieve credentials from the node. Defaults to using the master's instance profile.
	 * @return True if enabled.
	 */
	public boolean isEnableCredentialsFromNode() {
		return this.enableCredentialsFromNode;
	}

	/**
	 * Return the singleton instance.
	 *
	 * @return the one.
	 */
	@Nonnull
	public static PluginImpl getInstance() {
		return ExtensionList.lookup(PluginImpl.class).get(0);
	}

	/**
	 * Set enableCredentialsFromNode
	 * Default value is false.
	 *
	 * @param enableCredentialsFromNode whether to retrieve credentials from node or from master
	 */
	@DataBoundSetter
	public void setEnableCredentialsFromNode(boolean enableCredentialsFromNode) {
		this.enableCredentialsFromNode = enableCredentialsFromNode;
	}

}
