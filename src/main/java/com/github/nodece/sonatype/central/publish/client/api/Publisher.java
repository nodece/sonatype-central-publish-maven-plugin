/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.github.nodece.sonatype.central.publish.client.api;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public interface Publisher {
    CompletableFuture<Void> initialize(PublisherConfig config);

    CompletableFuture<String> upload(
            String deploymentName, PublishingType publishingType, String filename, InputStream inputStream);

    CompletableFuture<Void> publish(String deploymentId);

    CompletableFuture<DeploymentStatus> status(String deploymentId);

    CompletableFuture<Void> close();
}
