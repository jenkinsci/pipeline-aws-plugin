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

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class S3Utils {

	private static final int NOT_FOUND = 404;

	private S3Utils() {
	}

	/**
	 * Waits for a transfer, unwrapping the failure and staying interruptible.
	 *
	 * Two reasons not to call join() directly. It wraps whatever went wrong in a CompletionException,
	 * where v1's waitForCompletion threw the AmazonS3Exception itself, so a Jenkinsfile could catch
	 * the S3 error and the log showed the service message rather than a wrapper. And join() is not
	 * interruptible: it records the interrupt and keeps waiting, so aborting a build would leave the
	 * transfer running - on an agent, against a workspace Jenkins believes is free. get() throws
	 * InterruptedException, which the callers already declare.
	 */
	public static <T> T joinTransfer(CompletableFuture<T> future) throws InterruptedException {
		try {
			return future.get();
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new RuntimeException(cause);
		}
	}

	/**
	 * v2 dropped v1's doesObjectExist convenience method, so this restores it.
	 *
	 * v1 implemented it as getObjectMetadata plus "treat any 404 as absent", which means a missing
	 * bucket answered false rather than throwing, and a 403 propagated. Catching NoSuchKeyException
	 * alone would not reproduce that - a missing bucket raises NoSuchBucketException instead - so the
	 * status code is what is matched on, exactly as v1 did.
	 */
	public static boolean doesObjectExist(S3Client s3Client, String bucket, String key) {
		try {
			s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return true;
		} catch (S3Exception e) {
			if (e.statusCode() == NOT_FOUND) {
				return false;
			}
			throw e;
		}
	}
}
