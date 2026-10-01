/*
 * -
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2017 Taimos GmbH
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleSessionNameBuilderTest {

	@Test
	void shortNamesAreNotStripped() {
		String shortJobName = "shortName";
		String buildNumber = "1";
		final RoleSessionNameBuilder roleSessionNameBuilder = RoleSessionNameBuilder
				.withJobName(shortJobName)
				.withBuildNumber(buildNumber);
		final String result = roleSessionNameBuilder.build();
		assertEquals("Jenkins-shortName-1", result, "roleSessionNameBuilder should not be strapped");
	}

	@Test
	void nameLongerThanAWSLimitAreStripped() {
		String jobName = "s".repeat(64);
		String buildNumber = "123";
		final RoleSessionNameBuilder roleSessionNameBuilder = RoleSessionNameBuilder.withJobName(jobName)
				.withBuildNumber(buildNumber);
		final String result = roleSessionNameBuilder.build();
		assertEquals(64, result.length(), "The result should be equal to the limit");
	}

	@Test
	void nameEqualToAWSLimitAreStripped() {
		String jobName = "s".repeat(52);
		String buildNumber = "123";
		final RoleSessionNameBuilder roleSessionNameBuilder = RoleSessionNameBuilder.withJobName(jobName)
				.withBuildNumber(buildNumber);
		final String result = roleSessionNameBuilder.build();
		assertEquals(64, result.length(), "The result should be equal to the limit");
	}

	@Test
	void htmlEncodingJobName() {
		String jobName = "withHTMLEncoding%2FJobName";
		String buildNumber = "123";
		final RoleSessionNameBuilder roleSessionNameBuilder = RoleSessionNameBuilder
				.withJobName(jobName)
				.withBuildNumber(buildNumber);
		final String result = roleSessionNameBuilder.build();
		assertEquals("Jenkins-withHTMLEncoding-JobName-123", result, "The result should not have any encoded html characters");
	}

	@Test
	void htmlEncodingBuildNumber() {
		String jobName = "jobName";
		String buildNumber = "withHTMLEncoding%2FNumber";
		final RoleSessionNameBuilder roleSessionNameBuilder = RoleSessionNameBuilder
				.withJobName(jobName)
				.withBuildNumber(buildNumber);
		final String result = roleSessionNameBuilder.build();
		assertEquals("Jenkins-jobName-withHTMLEncoding-Number", result, "The result should not have any encoded html characters");
	}

	@Test
	void sanitizeJobName() {
		String jobName = "\"jobName' space / slash (paran)";
		String buildNumber = "(some)123";
		final RoleSessionNameBuilder roleSessionNameBuilder = RoleSessionNameBuilder
				.withJobName(jobName)
				.withBuildNumber(buildNumber);
		final String result = roleSessionNameBuilder.build();
		assertEquals("Jenkins-jobNamespace-slash-paran-some-123", result, "The result should not have any special characters");
	}
}
