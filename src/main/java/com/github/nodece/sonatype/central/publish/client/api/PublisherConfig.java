/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.github.nodece.sonatype.central.publish.client.api;

import java.net.URI;
import lombok.Data;
import lombok.ToString;

@Data
@lombok.Builder
@ToString
public class PublisherConfig {
    private URI uri;
    private Authentication authentication;

    public URI getUri() {
        return uri == null ? URI.create("https://central.sonatype.com/api/v1/") : uri;
    }
}
