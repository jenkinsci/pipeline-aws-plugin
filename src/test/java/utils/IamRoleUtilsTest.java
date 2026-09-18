package utils;

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

import org.junit.Test;
import org.junit.Assert;

import de.taimos.pipeline.aws.utils.IamRoleUtils;

public class IamRoleUtilsTest {

	@Test
	public void findPartitionWithRegionName() throws Exception {
		// example of type 'aws'
		Assert.assertEquals("aws", IamRoleUtils.selectPartitionName("us-east-1"));

		// example of type 'aws-cn'
		Assert.assertEquals("aws-cn", IamRoleUtils.selectPartitionName("cn-north-1"));

		// example of type 'aws-us-gov'
		Assert.assertEquals("aws-us-gov", IamRoleUtils.selectPartitionName("us-gov-west-1"));
		Assert.assertEquals("aws", IamRoleUtils.selectPartitionName("eu-west-1"));
		// no exception -> ok
	}

	/**
	 * withAWS defaults region to the empty string, so this is what withAWS(role: ..., roleAccount: ...)
	 * with no region reaches. v1 answered aws; v2's Region.of rejects a blank name, which would fail
	 * the step before the role was requested.
	 */
	@Test
	public void blankRegionFallsBackToTheAwsPartition() throws Exception {
		Assert.assertEquals("aws", IamRoleUtils.selectPartitionName(""));
		Assert.assertEquals("aws", IamRoleUtils.selectPartitionName("  "));
		Assert.assertEquals("aws", IamRoleUtils.selectPartitionName(null));
	}

	/**
	 * v1 synthesised a region for an unrecognised name rather than failing, and answered aws.
	 */
	@Test
	public void anUnknownRegionStillResolvesToAPartition() throws Exception {
		Assert.assertEquals("aws", IamRoleUtils.selectPartitionName("made-up-region"));
	}

	/**
	 * Region.of only rejects a blank name, so an untrimmed region would match no partition and fall
	 * through to aws - silently producing an arn:aws role ARN for a China-partition role.
	 */
	@Test
	public void surroundingWhitespaceDoesNotChangeThePartition() throws Exception {
		Assert.assertEquals("aws-cn", IamRoleUtils.selectPartitionName(" cn-north-1 "));
	}

}
