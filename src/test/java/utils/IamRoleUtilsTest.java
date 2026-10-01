package utils;
import org.junit.jupiter.api.Test;
import de.taimos.pipeline.aws.utils.IamRoleUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IamRoleUtilsTest {

	@Test
	void findPartitionWithRegionName() {
		// example of type 'aws'
		assertEquals("aws", IamRoleUtils.selectPartitionName("us-east-1"));

		// example of type 'aws-cn'
		assertEquals("aws-cn", IamRoleUtils.selectPartitionName("cn-north-1"));

		// example of type 'aws-us-gov'
		assertEquals("aws-us-gov", IamRoleUtils.selectPartitionName("us-gov-west-1"));
		assertEquals("aws", IamRoleUtils.selectPartitionName("eu-west-1"));
		// no exception -> ok
	}

	/**
	 * withAWS defaults region to the empty string, so this is what withAWS(role: ..., roleAccount: ...)
	 * with no region reaches. v1 answered aws; v2's Region.of rejects a blank name, which would fail
	 * the step before the role was requested.
	 */
	@Test
	void blankRegionFallsBackToTheAwsPartition() {
		assertEquals("aws", IamRoleUtils.selectPartitionName(""));
		assertEquals("aws", IamRoleUtils.selectPartitionName("  "));
		assertEquals("aws", IamRoleUtils.selectPartitionName(null));
	}

	/**
	 * v1 synthesised a region for an unrecognised name rather than failing, and answered aws.
	 */
	@Test
	void anUnknownRegionStillResolvesToAPartition() {
		assertEquals("aws", IamRoleUtils.selectPartitionName("made-up-region"));
	}

	/**
	 * Region.of only rejects a blank name, so an untrimmed region would match no partition and fall
	 * through to aws - silently producing an arn:aws role ARN for a China-partition role.
	 */
	@Test
	void surroundingWhitespaceDoesNotChangeThePartition() {
		assertEquals("aws-cn", IamRoleUtils.selectPartitionName(" cn-north-1 "));
	}

}
