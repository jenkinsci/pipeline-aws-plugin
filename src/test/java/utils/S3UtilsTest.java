package utils;

/*-
 * #%L
 * Pipeline: AWS Steps
 * %%
 * Copyright (C) 2026 Taimos GmbH
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

import de.taimos.pipeline.aws.utils.S3Utils;
import org.junit.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * v1's doesObjectExist was "getObjectMetadata, and treat any 404 as absent". The cases below are the
 * ones where a naive port to headObject plus catch NoSuchKeyException would diverge from it.
 */
public class S3UtilsTest {

	private final S3Client s3Client = Mockito.mock(S3Client.class);

	@Test
	public void presentObject() {
		Mockito.when(this.s3Client.headObject(Mockito.any(HeadObjectRequest.class)))
				.thenReturn(HeadObjectResponse.builder().build());

		assertThat(S3Utils.doesObjectExist(this.s3Client, "bucket", "key")).isTrue();
	}

	@Test
	public void missingObjectIsAbsentRatherThanAnError() {
		Mockito.when(this.s3Client.headObject(Mockito.any(HeadObjectRequest.class)))
				.thenThrow(NoSuchKeyException.builder().statusCode(404).build());

		assertThat(S3Utils.doesObjectExist(this.s3Client, "bucket", "key")).isFalse();
	}

	/**
	 * A missing bucket is a 404 too, and v1 answered false for it. NoSuchBucketException is not a
	 * NoSuchKeyException, so matching on the exception type alone would throw here instead.
	 */
	@Test
	public void missingBucketIsAlsoAbsent() {
		Mockito.when(this.s3Client.headObject(Mockito.any(HeadObjectRequest.class)))
				.thenThrow(NoSuchBucketException.builder().statusCode(404).build());

		assertThat(S3Utils.doesObjectExist(this.s3Client, "bucket", "key")).isFalse();
	}

	/**
	 * Access denied must not be reported as "not there" - that would turn a misconfigured bucket
	 * policy into a silently skipped s3Delete.
	 */
	@Test
	public void accessDeniedPropagates() {
		Mockito.when(this.s3Client.headObject(Mockito.any(HeadObjectRequest.class)))
				.thenThrow(S3Exception.builder()
						.statusCode(403)
						.awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").build())
						.build());

		assertThatThrownBy(() -> S3Utils.doesObjectExist(this.s3Client, "bucket", "key"))
				.isInstanceOf(S3Exception.class);
	}
}
