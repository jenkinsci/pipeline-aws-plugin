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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The object-shaping parameters s3Upload accepts, and their translation onto a v2 PutObjectRequest.
 *
 * v1 spread this across four near-identical blocks - the text upload, the single-file callable, and
 * an ObjectMetadataProvider in each of the two directory callables - which had already drifted apart:
 * the file-list provider set the SSE *algorithm* to the KMS key id, where the single-file path
 * correctly set it to aws:kms. Collecting the parameters here means the translation exists once and
 * the callables serialise one field instead of ten.
 *
 * Serializable because the upload callables carry it to the agent.
 */
public class S3UploadOptions implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Map<String, String> metadatas;
	private final Map<String, String> tags;
	private final CannedAcl acl;
	private final String cacheControl;
	private final String contentEncoding;
	private final String contentType;
	private final String contentDisposition;
	private final String kmsId;
	private final String sseAlgorithm;
	private final String redirectLocation;

	public S3UploadOptions(Map<String, String> metadatas, Map<String, String> tags, CannedAcl acl,
			String cacheControl, String contentEncoding, String contentType, String contentDisposition,
			String kmsId, String sseAlgorithm, String redirectLocation) {
		this.metadatas = metadatas == null ? null : new HashMap<>(metadatas);
		this.tags = tags == null ? null : new HashMap<>(tags);
		this.acl = acl;
		this.cacheControl = cacheControl;
		this.contentEncoding = contentEncoding;
		this.contentType = contentType;
		this.contentDisposition = contentDisposition;
		this.kmsId = kmsId;
		this.sseAlgorithm = sseAlgorithm;
		this.redirectLocation = redirectLocation;
	}

	public String getKmsId() {
		return this.kmsId;
	}

	private static boolean isSet(String value) {
		return value != null && !value.isEmpty();
	}

	/**
	 * Applies every option that is set. Unset options are left alone rather than written as null, so
	 * that S3 defaults still apply.
	 */
	public PutObjectRequest.Builder applyTo(PutObjectRequest.Builder request) {
		if (this.metadatas != null && !this.metadatas.isEmpty()) {
			request.metadata(this.metadatas);
		}
		if (isSet(this.cacheControl)) {
			request.cacheControl(this.cacheControl);
		}
		if (isSet(this.contentEncoding)) {
			request.contentEncoding(this.contentEncoding);
		}
		if (isSet(this.contentType)) {
			request.contentType(this.contentType);
		}
		if (isSet(this.contentDisposition)) {
			request.contentDisposition(this.contentDisposition);
		}
		if (isSet(this.sseAlgorithm)) {
			// The String overload, not fromValue: v2's generated fromValue maps an unmodelled value to
			// UNKNOWN_TO_SDK_VERSION, whose toString is the literal "null", so a typo or an algorithm this
			// SDK predates would reach S3 as x-amz-server-side-encryption: null and fail without naming
			// what was typed. v1 put the string on ObjectMetadata verbatim; this keeps that.
			request.serverSideEncryption(this.sseAlgorithm);
		}
		if (this.acl != null) {
			request.acl(this.acl.toObjectCannedACL());
		}
		if (this.tags != null && !this.tags.isEmpty()) {
			List<Tag> tagList = new ArrayList<>();
			for (Map.Entry<String, String> entry : this.tags.entrySet()) {
				tagList.add(Tag.builder().key(entry.getKey()).value(entry.getValue()).build());
			}
			request.tagging(Tagging.builder().tagSet(tagList).build());
		}
		// v1's SSEAwsKeyManagementParams set the key and the algorithm together; on the v2 request
		// they are separate fields, and a key without aws:kms leaves the object unencrypted. Applied
		// after sseAlgorithm so an explicit kmsId wins, as it did in v1.
		if (isSet(this.kmsId)) {
			request.ssekmsKeyId(this.kmsId).serverSideEncryption(ServerSideEncryption.AWS_KMS);
		}
		if (isSet(this.redirectLocation)) {
			request.websiteRedirectLocation(this.redirectLocation);
		}
		return request;
	}
}
