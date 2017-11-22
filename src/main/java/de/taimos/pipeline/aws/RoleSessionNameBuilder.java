package de.taimos.pipeline.aws;

/*-
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2016 - 2017 Taimos GmbH
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

import com.google.common.base.Joiner;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;

final class RoleSessionNameBuilder {
	private static final int ROLE_SESSION_NAME_MAX_LENGTH = 64;
	private static final String SESSION_NAME_PREFIX = "Jenkins";
	private static final int NUMBER_OF_SEPARATORS = 2;
	private final String jobName;
	private String buildNumber;

	private RoleSessionNameBuilder(String jobName) {
		this.jobName = jobName;
	}

	String build() {
		String jobNameWithoutEncoding = "";
		try {
			jobNameWithoutEncoding = java.net.URLDecoder.decode(this.jobName, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// UTF-8 is always supported
		}
		final String jobNameWithoutWhitespaces = jobNameWithoutEncoding.replace(" ", "");
		final String jobNameWithoutSlashes = jobNameWithoutWhitespaces.replace("/", "-");

		final int maxJobNameLength = ROLE_SESSION_NAME_MAX_LENGTH - (SESSION_NAME_PREFIX.length() + this.buildNumber.length() + NUMBER_OF_SEPARATORS);

		final int jobNameLength = jobNameWithoutSlashes.length();
		String finalJobName = jobNameWithoutSlashes;
		if (jobNameLength > maxJobNameLength) {
			finalJobName = jobNameWithoutSlashes.substring(0, maxJobNameLength);
		}
		return Joiner.on("-").join(SESSION_NAME_PREFIX, finalJobName, this.buildNumber);
	}

	static RoleSessionNameBuilder withJobName(final String jobName) {
		return new RoleSessionNameBuilder(jobName);
	}

	RoleSessionNameBuilder withBuildNumber(final String buildNumber) {
		this.buildNumber = buildNumber;
		return this;
	}
}
