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

import de.taimos.pipeline.aws.AbstractS3Step.S3ClientOptions;
import org.junit.Test;
import software.amazon.awssdk.services.s3.multipart.MultipartConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These four numbers come from v1's TransferManagerConfiguration, read off the v1 jar. They are not
 * arbitrary tuning:
 *
 * - the thresholds decide when an object gets a multipart ETag (a hash of part hashes with a -N
 *   suffix rather than the content MD5), which a pipeline comparing ETags against a local digest can
 *   observe;
 * - the part sizes decide request count, since v2 sizes parts as
 *   max(minimumPartSizeInBytes, ceil(length / 10000)).
 *
 * The SDK's own default is 8 MiB for everything, so none of this survives without being set.
 */
public class S3MultipartConfigurationTest {

	private static final long MIB = 1024 * 1024;
	private static final long GIB = 1024 * MIB;

	@Test
	public void uploadsUseV1sUploadFigures() {
		MultipartConfiguration configuration = S3ClientOptions.uploadMultipartConfiguration();

		assertThat(configuration.thresholdInBytes()).isEqualTo(16 * MIB);
		assertThat(configuration.minimumPartSizeInBytes()).isEqualTo(5 * MIB);
	}

	/**
	 * Copy parts are transferred server-side and v1 sized them far more coarsely. At the upload part
	 * size a copy just over the threshold would be split into roughly 1024 UploadPartCopy calls
	 * instead of about 52.
	 */
	@Test
	public void copiesUseV1sCopyFigures() {
		MultipartConfiguration configuration = S3ClientOptions.copyMultipartConfiguration();

		assertThat(configuration.thresholdInBytes()).isEqualTo(5 * GIB);
		assertThat(configuration.minimumPartSizeInBytes()).isEqualTo(100 * MIB);
	}

	/**
	 * Downloads get no multipart configuration at all. v1 had none, and v2's multipart download is
	 * part-number driven - one GetObject per part the object was uploaded with - so enabling it would
	 * multiply requests for objects this plugin uploaded in parts while buying nothing, since a
	 * single-request download of a large object is never rejected.
	 *
	 * This pins the decision, not the wiring: createS3AsyncClientBuilderForDownload could still be
	 * pointed back at the upload configuration without failing anything here. Asserting the wiring
	 * needs a built client, which needs a region and credentials, and was judged not worth it.
	 */
	@Test
	public void downloadsGetNoMultipartConfiguration() {
		assertThat(S3ClientOptions.downloadMultipartConfiguration()).isNull();
	}
}
