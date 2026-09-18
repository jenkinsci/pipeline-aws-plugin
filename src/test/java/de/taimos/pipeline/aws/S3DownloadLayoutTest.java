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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Publisher;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedDirectoryDownload;
import software.amazon.awssdk.transfer.s3.model.DownloadDirectoryRequest;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a directory download puts files on disk.
 *
 * v1 recreated the object's full key under the destination, so s3Download(path: 'a/b/', file: 'out')
 * produced out/a/b/x.txt. v2's DownloadDirectoryHelper resolves each key relative to the listing
 * prefix instead, which would put the same object at out/x.txt - a silent change: the download
 * succeeds, and only a later step reading the file by its full key path fails.
 *
 * This drives a real S3TransferManager over a stubbed asynchronous client, so the assertion is on
 * actual files rather than on a request object. Mocking the transfer manager, as the other download
 * tests do, cannot observe this at all.
 */
public class S3DownloadLayoutTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Rule
	public Timeout timeout = Timeout.seconds(60);

	/**
	 * Keys carry an explicit size because the SDK's default filter distinguishes on it: a
	 * delimiter-terminated key of size zero is a folder marker it already skips, one with content is
	 * not.
	 */
	private static S3AsyncClient stubClientReturning(Map<String, Long> keys) {
		S3AsyncClient client = Mockito.mock(S3AsyncClient.class);

		// stub the underlying call first: the real publisher drives it, so it must already answer
		Mockito.when(client.listObjectsV2(Mockito.any(ListObjectsV2Request.class)))
				.thenReturn(CompletableFuture.completedFuture(ListObjectsV2Response.builder()
						.contents(keys.entrySet().stream()
								.map(e -> S3Object.builder().key(e.getKey()).size(e.getValue()).build())
								.collect(Collectors.toList()))
						.isTruncated(false)
						.build()));
		Mockito.when(client.listObjectsV2Paginator(Mockito.any(ListObjectsV2Request.class)))
				.thenAnswer((Answer<ListObjectsV2Publisher>) invocation ->
						new ListObjectsV2Publisher(client, invocation.getArgument(0)));

		Mockito.when(client.getObject(Mockito.any(GetObjectRequest.class), Mockito.any(AsyncResponseTransformer.class)))
				.thenAnswer(invocation -> {
					AsyncResponseTransformer<GetObjectResponse, ?> transformer = invocation.getArgument(1);
					CompletableFuture<?> future = transformer.prepare();
					transformer.onResponse(GetObjectResponse.builder().contentLength(4L).build());
					transformer.onStream(AsyncRequestBody.fromString("data"));
					return future;
				});
		return client;
	}

	private CompletedDirectoryDownload download(String prefix, Map<String, Long> keys, File destination) {
		try (S3AsyncClient client = stubClientReturning(keys);
				S3TransferManager mgr = S3TransferManager.builder().s3Client(client).build()) {
			// exactly the request the step issues - layout depends on the destination and the listing
			// prefix together, so a request rebuilt here could stop matching without anyone noticing
			return mgr.downloadDirectory(
					S3DownloadStep.downloadDirectoryRequest("my-bucket", destination, prefix))
					.completionFuture().join();
		}
	}

	/**
	 * The case that changed: an object under a requested prefix keeps its full key path under the
	 * destination, as it did under v1.
	 */
	@Test
	public void aPrefixedDownloadKeepsTheFullKeyPath() {
		File destination = this.folder.getRoot();

		CompletedDirectoryDownload completed = this.download("a/b/",
				sized("a/b/x.txt", 4L, "a/b/nested/y.txt", 4L), destination);

		assertThat(completed.failedTransfers()).isEmpty();
		assertThat(new File(destination, "a/b/x.txt")).exists();
		assertThat(new File(destination, "a/b/nested/y.txt")).exists();
	}

	/**
	 * Without a prefix the two layouts agree, so this pins that the fix did not shift the no-path case.
	 */
	@Test
	public void anUnprefixedDownloadIsUnchanged() {
		File destination = this.folder.getRoot();

		CompletedDirectoryDownload completed = this.download("",
				sized("top.txt", 4L, "sub/deeper.txt", 4L), destination);

		assertThat(completed.failedTransfers()).isEmpty();
		assertThat(new File(destination, "top.txt")).exists();
		assertThat(new File(destination, "sub/deeper.txt")).exists();
	}

	/**
	 * A zero-byte delimiter-terminated key is the folder marker the S3 console creates, and the SDK's
	 * default filter already excludes it - which is why the step's own filter is a widening rather
	 * than the thing that keeps console folders from failing builds.
	 *
	 * This deliberately issues a request *without* the step's filter. Going through
	 * downloadDirectoryRequest would prove nothing here: that filter rejects any key ending in the
	 * delimiter before the default is consulted, so the test would pass whether or not the SDK
	 * excluded zero-byte markers - which is the claim being made.
	 */
	@Test
	public void theSdkDefaultAlreadySkipsZeroByteFolderMarkers() {
		File destination = this.folder.getRoot();
		Map<String, Long> keys = sized("a/b/", 0L, "a/b/x.txt", 4L);

		CompletedDirectoryDownload completed;
		try (S3AsyncClient client = stubClientReturning(keys);
				S3TransferManager mgr = S3TransferManager.builder().s3Client(client).build()) {
			completed = mgr.downloadDirectory(DownloadDirectoryRequest.builder()
					.bucket("my-bucket")
					.destination(S3DownloadStep.destinationFor(destination, "a/b/"))
					.listObjectsV2RequestTransformer(l -> l.prefix("a/b/"))
					.build()).completionFuture().join();
		}

		assertThat(completed.failedTransfers()).isEmpty();
		assertThat(new File(destination, "a/b/x.txt")).exists();
	}

	/**
	 * A delimiter-terminated key *with* content is what the step's own filter adds: the SDK default
	 * accepts it, and v2 normalises it to an empty relative path that resolves to the destination
	 * directory itself - unwritable as a file, so a failed transfer and hence a failed build.
	 */
	@Test
	public void delimiterTerminatedKeysWithContentAreSkippedToo() {
		File destination = this.folder.getRoot();

		CompletedDirectoryDownload completed = this.download("a/b/",
				sized("a/b/", 4L, "a/b/x.txt", 4L), destination);

		assertThat(completed.failedTransfers()).isEmpty();
		assertThat(new File(destination, "a/b/x.txt")).exists();
	}

	/**
	 * Alternating key and size, so a case needing a third object does not need a new overload.
	 *
	 * Number rather than Long, because sized("x", 4) with an int literal would otherwise compile and
	 * fail at runtime with a ClassCastException from inside here.
	 */
	private static Map<String, Long> sized(Object... keysAndSizes) {
		if (keysAndSizes.length % 2 != 0) {
			throw new IllegalArgumentException("expected alternating key and size, got " + keysAndSizes.length + " arguments");
		}
		Map<String, Long> keys = new LinkedHashMap<>();
		for (int i = 0; i < keysAndSizes.length; i += 2) {
			keys.put((String) keysAndSizes[i], ((Number) keysAndSizes[i + 1]).longValue());
		}
		return keys;
	}
}
