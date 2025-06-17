/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class FutureUtils {
    public static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    public static Throwable unwrapCompletionException(Throwable ex) {
        if (ex instanceof CompletionException) {
            return unwrapCompletionException(ex.getCause());
        } else if (ex instanceof ExecutionException) {
            return unwrapCompletionException(ex.getCause());
        } else {
            return ex;
        }
    }
}
