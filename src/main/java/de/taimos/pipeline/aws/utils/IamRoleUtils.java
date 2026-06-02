package de.taimos.pipeline.aws.utils;

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

import java.util.regex.Pattern;

import software.amazon.awssdk.regions.Region;

public final class IamRoleUtils {

	private static final Pattern IAM_ROLE_PATTERN = Pattern.compile("arn:(aws|aws-cn|aws-us-gov):iam::[0-9]{12}:role/([\\w+=,.@/-]{1,512}/)?[\\w+=,.@-]{1,64}");
	// source: http://docs.aws.amazon.com/IAM/latest/UserGuide/reference_iam-limits.html

	private IamRoleUtils() {
		// hidden constructor
	}

	public static String selectPartitionName(String region) {
		return Region.of(region).metadata().partition().name();
	}

	public static boolean validRoleArn(String role) {
		return IAM_ROLE_PATTERN.matcher(role).matches();
	}

}
