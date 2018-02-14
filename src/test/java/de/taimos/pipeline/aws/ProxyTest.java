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

import org.junit.Assert;
import org.junit.Test;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;

import hudson.EnvVars;

public class ProxyTest {

	@Test
	public void shouldNotChangeIfNotPresent() throws Exception {
		ClientConfiguration config = new ClientConfiguration();
		config.setProtocol(Protocol.HTTPS);
		ProxyConfiguration.configure(new EnvVars(), config);

		Assert.assertNull(config.getProxyUsername());
		Assert.assertNull(config.getProxyPassword());
		Assert.assertNull(config.getProxyHost());
		Assert.assertEquals(-1, config.getProxyPort());
	}

	@Test
	public void shouldParseProxy() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(ProxyConfiguration.HTTPS_PROXY, "http://127.0.0.1:8888/");
		ClientConfiguration config = new ClientConfiguration();
		config.setProtocol(Protocol.HTTPS);
		ProxyConfiguration.configure(vars, config);

		Assert.assertNull(config.getProxyUsername());
		Assert.assertNull(config.getProxyPassword());
		Assert.assertEquals("127.0.0.1", config.getProxyHost());
		Assert.assertEquals(8888, config.getProxyPort());
	}

	@Test
	public void shouldParseProxyWithoutPort() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(ProxyConfiguration.HTTPS_PROXY, "http://127.0.0.1/");
		ClientConfiguration config = new ClientConfiguration();
		config.setProtocol(Protocol.HTTPS);
		ProxyConfiguration.configure(vars, config);

		Assert.assertNull(config.getProxyUsername());
		Assert.assertNull(config.getProxyPassword());
		Assert.assertEquals("127.0.0.1", config.getProxyHost());
		Assert.assertEquals(443, config.getProxyPort());
	}

	@Test
	public void shouldParseProxyLowerCase() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(ProxyConfiguration.HTTPS_PROXY_LC, "http://127.0.0.1:8888/");
		ClientConfiguration config = new ClientConfiguration();
		config.setProtocol(Protocol.HTTPS);
		ProxyConfiguration.configure(vars, config);

		Assert.assertNull(config.getProxyUsername());
		Assert.assertNull(config.getProxyPassword());
		Assert.assertEquals("127.0.0.1", config.getProxyHost());
		Assert.assertEquals(8888, config.getProxyPort());
	}

	@Test
	public void shouldParseProxyWithAuth() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(ProxyConfiguration.HTTPS_PROXY, "http://foo:bar@127.0.0.1:8888/");
		ClientConfiguration config = new ClientConfiguration();
		config.setProtocol(Protocol.HTTPS);
		ProxyConfiguration.configure(vars, config);

		Assert.assertEquals("foo", config.getProxyUsername());
		Assert.assertEquals("bar", config.getProxyPassword());
		Assert.assertEquals("127.0.0.1", config.getProxyHost());
		Assert.assertEquals(8888, config.getProxyPort());
	}

	@Test
	public void shouldSetNonProxyHosts() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(ProxyConfiguration.NO_PROXY, "127.0.0.1,localhost");
		vars.put(ProxyConfiguration.HTTPS_PROXY, "http://127.0.0.1:8888/");
		ClientConfiguration config = new ClientConfiguration();
		config.setProtocol(Protocol.HTTPS);
		ProxyConfiguration.configure(vars, config);

		Assert.assertEquals("127.0.0.1|localhost", config.getNonProxyHosts());
	}

	@Test
	public void shouldSetNonProxyHostsLowerCase() throws Exception {
		EnvVars vars = new EnvVars();
		vars.put(ProxyConfiguration.NO_PROXY_LC, "127.0.0.1,localhost");
		vars.put(ProxyConfiguration.HTTPS_PROXY, "http://127.0.0.1:8888/");
		ClientConfiguration config = new ClientConfiguration();
		config.setProtocol(Protocol.HTTPS);
		ProxyConfiguration.configure(vars, config);

		Assert.assertEquals("127.0.0.1|localhost", config.getNonProxyHosts());
	}

}
