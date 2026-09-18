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

import de.taimos.pipeline.aws.utils.CannedAcl;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stapler binds acl: 'PublicRead' by enum constant name, so these names are pipeline syntax rather
 * than an implementation detail: renaming one to its v2 spelling breaks every Jenkinsfile using it.
 * The names are pinned here as strings so that cannot happen silently.
 */
public class CannedAclTest {

	@Test
	public void v1SpellingsStillBind() {
		assertThat(CannedAcl.valueOf("Private").toObjectCannedACL()).isEqualTo(ObjectCannedACL.PRIVATE);
		assertThat(CannedAcl.valueOf("PublicRead").toObjectCannedACL()).isEqualTo(ObjectCannedACL.PUBLIC_READ);
		assertThat(CannedAcl.valueOf("PublicReadWrite").toObjectCannedACL()).isEqualTo(ObjectCannedACL.PUBLIC_READ_WRITE);
		assertThat(CannedAcl.valueOf("AuthenticatedRead").toObjectCannedACL()).isEqualTo(ObjectCannedACL.AUTHENTICATED_READ);
		assertThat(CannedAcl.valueOf("BucketOwnerRead").toObjectCannedACL()).isEqualTo(ObjectCannedACL.BUCKET_OWNER_READ);
		assertThat(CannedAcl.valueOf("BucketOwnerFullControl").toObjectCannedACL()).isEqualTo(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL);
		assertThat(CannedAcl.valueOf("AwsExecRead").toObjectCannedACL()).isEqualTo(ObjectCannedACL.AWS_EXEC_READ);
	}

	/**
	 * Every constant maps to a real ACL - none may fall through to the SDK's unknown sentinel, which
	 * would render as "null" on the wire rather than failing.
	 */
	@Test
	public void noneMapToTheUnknownSentinel() {
		for (CannedAcl acl : CannedAcl.values()) {
			assertThat(acl.toObjectCannedACL()).isNotEqualTo(ObjectCannedACL.UNKNOWN_TO_SDK_VERSION);
		}
	}
}
