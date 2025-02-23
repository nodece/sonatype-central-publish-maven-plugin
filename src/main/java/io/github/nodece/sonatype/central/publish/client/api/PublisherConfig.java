/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.client.api;

import java.net.URI;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PublisherConfig {
    private URI uri;
    private Authentication authentication;

    public URI getUri() {
        return uri == null ? URI.create("https://central.sonatype.com/api/v1/") : uri;
    }
}
