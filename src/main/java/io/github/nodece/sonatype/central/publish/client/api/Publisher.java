/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.client.api;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface Publisher {
    CompletableFuture<Void> initialize(PublisherConfig config);

    /**
     * Uploads a bundle, sending the file size as the HTTP {@code Content-Length} to avoid chunked transfer encoding.
     */
    CompletableFuture<String> upload(String deploymentName, PublishingType publishingType, Path bundlePath);

    CompletableFuture<DeploymentStatus> status(String deploymentId);

    CompletableFuture<Void> close();
}
