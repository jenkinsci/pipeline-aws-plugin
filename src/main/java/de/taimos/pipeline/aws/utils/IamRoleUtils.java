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

import software.amazon.awssdk.regions.PartitionMetadata;
import software.amazon.awssdk.regions.Region;

public final class IamRoleUtils {

	// length limits per http://docs.aws.amazon.com/IAM/latest/UserGuide/reference_iam-limits.html
	private static final Pattern IAM_ROLE_PATTERN = Pattern.compile("arn:(aws|aws-cn|aws-us-gov):iam::[0-9]{12}:role/([\\w+=,.@/-]{1,512}/)?[\\w+=,.@-]{1,64}");

	private static final String DEFAULT_PARTITION = "aws";

	private IamRoleUtils() {
		// hidden constructor
	}

	/**
	 * Checked against v1 for us-east-1 (aws), cn-north-1 (aws-cn), us-gov-west-1 (aws-us-gov),
	 * eu-west-1 (aws) and an unknown region name, which both resolve to aws rather than failing.
	 *
	 * The blank case needs handling explicitly: withAWS defaults region to the empty string, so
	 * withAWS(role: ..., roleAccount: ...) with no region reaches here with "". v1 answered aws for
	 * that, while v2's Region.of rejects a blank name outright - which would turn a working pipeline
	 * into an IllegalArgumentException before the role was even requested.
	 *
	 * The trimmed value is what gets resolved, not just what gets tested: Region.of only rejects a
	 * blank name, so " cn-north-1 " would otherwise match no partition and fall through to aws,
	 * silently building an arn:aws role ARN for a China-partition role.
	 */
	public static String selectPartitionName(String region) {
		String trimmed = region == null ? "" : region.trim();
		if (trimmed.isEmpty()) {
			return DEFAULT_PARTITION;
		}
		return PartitionMetadata.of(Region.of(trimmed)).id();
	}

	public static boolean validRoleArn(String role) {
		return (IAM_ROLE_PATTERN.matcher(role).matches());
	}

}
