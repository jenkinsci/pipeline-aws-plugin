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

package de.taimos.pipeline.aws.utils;

import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

/**
 * The canned ACL names that s3Upload and s3Copy accept.
 *
 * These are deliberately spelled as SDK v1 spelled them - acl: 'PublicRead', not 'PUBLIC_READ' -
 * because Stapler binds the parameter by enum constant name, so renaming them to the v2 spellings
 * would break every existing Jenkinsfile that sets one. Keeping an enum rather than taking a String
 * also keeps the Snippet Generator's dropdown and its up-front validation.
 *
 * v1's LogDeliveryWrite is absent: it is a bucket ACL, and v2 models object and bucket canned ACLs
 * separately, so there is no object-level counterpart to map it to. S3 rejected it on an object
 * under v1 as well, so what changes is that the build now fails when the value is bound rather than
 * when the request is sent.
 */
public enum CannedAcl {

	Private(ObjectCannedACL.PRIVATE),
	PublicRead(ObjectCannedACL.PUBLIC_READ),
	PublicReadWrite(ObjectCannedACL.PUBLIC_READ_WRITE),
	AuthenticatedRead(ObjectCannedACL.AUTHENTICATED_READ),
	BucketOwnerRead(ObjectCannedACL.BUCKET_OWNER_READ),
	BucketOwnerFullControl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL),
	AwsExecRead(ObjectCannedACL.AWS_EXEC_READ);

	private final ObjectCannedACL objectCannedACL;

	CannedAcl(ObjectCannedACL objectCannedACL) {
		this.objectCannedACL = objectCannedACL;
	}

	public ObjectCannedACL toObjectCannedACL() {
		return this.objectCannedACL;
	}
}
