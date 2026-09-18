/*
 * -
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

package de.taimos.pipeline.aws;

import de.taimos.pipeline.aws.utils.CannedAcl;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This translation replaced four near-identical blocks that had already drifted apart in v1 - one of
 * them sent the KMS key id as the encryption algorithm - so it is worth pinning directly rather than
 * only through the steps.
 */
public class S3UploadOptionsTest {

	private static S3UploadOptions options(Map<String, String> metadatas, Map<String, String> tags, CannedAcl acl,
			String cacheControl, String contentEncoding, String contentType, String contentDisposition,
			String kmsId, String sseAlgorithm, String redirectLocation) {
		return new S3UploadOptions(metadatas, tags, acl, cacheControl, contentEncoding, contentType,
				contentDisposition, kmsId, sseAlgorithm, redirectLocation);
	}

	private static PutObjectRequest apply(S3UploadOptions options) {
		return options.applyTo(PutObjectRequest.builder().bucket("b").key("k")).build();
	}

	/**
	 * The bug this refactor exists to fix: the file-list path used to set the algorithm to the key id.
	 * A key without aws:kms leaves the object unencrypted.
	 */
	@Test
	public void kmsIdSetsBothTheKeyAndTheAlgorithm() {
		PutObjectRequest request = apply(options(null, null, null, null, null, null, null, "my-key", null, null));

		assertThat(request.ssekmsKeyId()).isEqualTo("my-key");
		assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
	}

	@Test
	public void sseAlgorithmAloneIsPassedThrough() {
		PutObjectRequest request = apply(options(null, null, null, null, null, null, null, null, "AES256", null));

		assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
		assertThat(request.ssekmsKeyId()).isNull();
	}

	/**
	 * sseAlgorithm is a free-form pipeline string. v2's fromValue maps anything it does not model to
	 * UNKNOWN_TO_SDK_VERSION, whose string form is the literal "null", so routing it through the enum
	 * sent x-amz-server-side-encryption: null and S3 reported an error that never named what was
	 * typed. Asserted on the string accessor because the enum one reads UNKNOWN_TO_SDK_VERSION either
	 * way - which is why the pre-existing AES256 tests above passed with the bug present.
	 */
	@Test
	public void anUnmodelledSseAlgorithmReachesS3VerbatimRatherThanAsNull() {
		PutObjectRequest request = apply(options(null, null, null, null, null, null, null, null, "AES-256", null));

		assertThat(request.serverSideEncryptionAsString()).isEqualTo("AES-256");
	}

	/**
	 * v1 applied SSEAwsKeyManagementParams after the metadata's SSE algorithm, so a kmsId won.
	 */
	@Test
	public void kmsIdWinsOverAnExplicitSseAlgorithm() {
		PutObjectRequest request = apply(options(null, null, null, null, null, null, null, "my-key", "AES256", null));

		assertThat(request.serverSideEncryption()).isEqualTo(ServerSideEncryption.AWS_KMS);
		assertThat(request.ssekmsKeyId()).isEqualTo("my-key");
	}

	@Test
	public void everyOtherFieldReachesTheRequest() {
		Map<String, String> metadatas = new HashMap<>();
		metadatas.put("k1", "v1");

		PutObjectRequest request = apply(options(metadatas, Collections.singletonMap("t1", "tv1"),
				CannedAcl.PublicRead, "no-cache", "gzip", "text/plain", "attachment", null, null, "/elsewhere"));

		assertThat(request.metadata()).containsEntry("k1", "v1");
		// the SDK renders Tagging onto the request as the x-amz-tagging query string
		assertThat(request.tagging()).isEqualTo("t1=tv1");
		assertThat(request.acl()).isEqualTo(ObjectCannedACL.PUBLIC_READ);
		assertThat(request.cacheControl()).isEqualTo("no-cache");
		assertThat(request.contentEncoding()).isEqualTo("gzip");
		assertThat(request.contentType()).isEqualTo("text/plain");
		assertThat(request.contentDisposition()).isEqualTo("attachment");
		assertThat(request.websiteRedirectLocation()).isEqualTo("/elsewhere");
	}

	/**
	 * Unset options must not be written as nulls, or they would override the S3 defaults - and, for a
	 * directory upload, the bucket and key the transfer manager already computed.
	 */
	@Test
	public void unsetOptionsLeaveTheRequestAlone() {
		PutObjectRequest request = apply(options(Collections.emptyMap(), Collections.emptyMap(),
				null, "", "", "", "", "", "", ""));

		assertThat(request.bucket()).isEqualTo("b");
		assertThat(request.key()).isEqualTo("k");
		assertThat(request.metadata()).isEmpty();
		assertThat(request.tagging()).isNull();
		assertThat(request.acl()).isNull();
		assertThat(request.cacheControl()).isNull();
		assertThat(request.serverSideEncryption()).isNull();
		assertThat(request.websiteRedirectLocation()).isNull();
	}
}
