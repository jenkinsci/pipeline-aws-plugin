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

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

import hudson.EnvVars;

public class AWSClientFactory {
	
	static final String AWS_PROFILE = "AWS_PROFILE";
	static final String AWS_DEFAULT_PROFILE = "AWS_DEFAULT_PROFILE";
	static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
	static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
	static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";
	static final String AWS_DEFAULT_REGION = "AWS_DEFAULT_REGION";
	static final String AWS_REGION = "AWS_REGION";
	
	static <T extends AmazonWebServiceClient> T create(Class<T> clazz, EnvVars vars) {
		Region region = AWSClientFactory.getRegion(vars);
		final AWSCredentialsProvider provider = AWSClientFactory.getCredentials(vars);
		return region.createClient(clazz, provider, null);
	}
	
	private static AWSCredentialsProvider getCredentials(EnvVars vars) {
		AWSCredentialsProvider provider = handleStaticCredentials(vars);
		if (provider != null) {
			return provider;
		}
		
		provider = handleProfile(vars);
		if (provider != null) {
			return provider;
		}
		
		return new DefaultAWSCredentialsProviderChain();
	}
	
	private static AWSCredentialsProvider handleProfile(EnvVars vars) {
		String profile = vars.get(AWS_PROFILE, vars.get(AWS_DEFAULT_PROFILE));
		if (profile != null) {
			return new ProfileCredentialsProvider(profile);
		}
		return null;
	}
	
	private static AWSCredentialsProvider handleStaticCredentials(EnvVars vars) {
		String accessKey = vars.get(AWS_ACCESS_KEY_ID);
		String secretAccessKey = vars.get(AWS_SECRET_ACCESS_KEY);
		if (accessKey != null && secretAccessKey != null) {
			String sessionToken = vars.get(AWS_SESSION_TOKEN);
			if (sessionToken != null) {
				return new AWSStaticCredentialsProvider(new BasicSessionCredentials(accessKey, secretAccessKey, sessionToken));
			}
			return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretAccessKey));
		}
		return null;
	}
	
	private static Region getRegion(EnvVars vars) {
		if (vars.get(AWS_DEFAULT_REGION) != null) {
			return Region.getRegion(Regions.fromName(vars.get(AWS_DEFAULT_REGION)));
		}
		if (vars.get(AWS_REGION) != null) {
			return Region.getRegion(Regions.fromName(vars.get(AWS_REGION)));
		}
		if (System.getenv(AWS_DEFAULT_REGION) != null) {
			return Region.getRegion(Regions.fromName(System.getenv(AWS_DEFAULT_REGION)));
		}
		if (System.getenv(AWS_REGION) != null) {
			return Region.getRegion(Regions.fromName(System.getenv(AWS_REGION)));
		}
		Region currentRegion = Regions.getCurrentRegion();
		if (currentRegion != null) {
			return currentRegion;
		}
		return Region.getRegion(Regions.DEFAULT_REGION);
	}
}
