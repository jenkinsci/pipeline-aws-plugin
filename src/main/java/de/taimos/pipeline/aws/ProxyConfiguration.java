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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hudson.EnvVars;
import jenkins.model.Jenkins;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

class ProxyConfiguration {

	static final String HTTP_PROXY = "HTTP_PROXY";
	static final String HTTP_PROXY_LC = "http_proxy";
	static final String HTTPS_PROXY = "HTTPS_PROXY";
	static final String HTTPS_PROXY_LC = "https_proxy";
	static final String NO_PROXY = "NO_PROXY";
	static final String NO_PROXY_LC = "no_proxy";

	private static final String PROXY_PATTERN = "(https?)://(([^:]+)(:(.+))?@)?([\\da-zA-Z.-]+)(:(\\d+))?/?";

	private static final int HTTP_PORT = 80;
	private static final int HTTPS_PORT = 443;

	private ProxyConfiguration() {
		// hidden constructor
	}

	static void configure(EnvVars vars, SdkSyncClientBuilder<?, ?> config) {
		software.amazon.awssdk.http.apache.ProxyConfiguration.Builder builder = software.amazon.awssdk.http.apache.ProxyConfiguration.builder();

        try {
            useJenkinsProxy(builder);
			if (config.httpClient() == Protocol.HTTP) {
				configureHTTP(vars, builder);
			} else {
				configureHTTPS(vars, builder);
			}
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

		configureNonProxyHosts(vars, builder);
		ApacheHttpClient httpClient = ApacheHttpClient.builder().proxyConfiguration(builder.build()).build();
		config.httpClient(httpClient);
	}

	private static void useJenkinsProxy(software.amazon.awssdk.http.apache.ProxyConfiguration.Builder config) throws URISyntaxException {
		if (Jenkins.getInstance() != null) {
			hudson.ProxyConfiguration proxyConfiguration = Jenkins.getInstance().proxy;
			if (proxyConfiguration != null) {
				config.endpoint(new URI(proxyConfiguration.name +":" + proxyConfiguration.port));
				config.username(proxyConfiguration.getUserName());
				config.password(proxyConfiguration.getPassword());

				if (proxyConfiguration.getNoProxyHost() != null) {
					String[] noProxyParts = proxyConfiguration.getNoProxyHost().split("[ \t\n,|]+");
					config.nonProxyHosts(Set.of(noProxyParts));
				}
			}
		}
	}

	private static void configureNonProxyHosts(EnvVars vars, software.amazon.awssdk.http.apache.ProxyConfiguration.Builder config) {
		String noProxy = vars.get(NO_PROXY, vars.get(NO_PROXY_LC));
		if (noProxy != null) {
			config.nonProxyHosts(Set.of(noProxy.split(",")));
		}
	}

	private static void configureHTTP(EnvVars vars, software.amazon.awssdk.http.apache.ProxyConfiguration.Builder config) throws URISyntaxException {
		String env = vars.get(HTTP_PROXY, vars.get(HTTP_PROXY_LC));
		if (env != null) {
			configureProxy(config, env, HTTP_PORT);
		}
	}

	private static void configureHTTPS(EnvVars vars, software.amazon.awssdk.http.apache.ProxyConfiguration.Builder config) throws URISyntaxException {
		String env = vars.get(HTTPS_PROXY, vars.get(HTTPS_PROXY_LC));
		if (env != null) {
			configureProxy(config, env, HTTPS_PORT);
		}
	}

	private static void configureProxy(software.amazon.awssdk.http.apache.ProxyConfiguration.Builder config, String env, int defaultPort) throws URISyntaxException {
		Pattern pattern = Pattern.compile(PROXY_PATTERN);
		Matcher matcher = pattern.matcher(env);
		if (matcher.matches()) {
			if (matcher.group(3) != null) {
				config.username(matcher.group(3));
			}
			if (matcher.group(5) != null) {
				config.password(matcher.group(5));
			}
			if (matcher.group(8) != null) {
				config.endpoint(new URI(matcher.group(6) + ":" + Integer.parseInt(matcher.group(8))));
			} else {
				config.endpoint(new URI(matcher.group(6) + ":" +defaultPort));
			}
		}
	}

}
