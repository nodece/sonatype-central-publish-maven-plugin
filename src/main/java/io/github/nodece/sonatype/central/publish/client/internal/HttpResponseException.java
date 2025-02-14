/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.client.internal;

import lombok.Getter;
import org.asynchttpclient.Response;

@Getter
public class HttpResponseException extends RuntimeException {
    private final Response response;

    public HttpResponseException(Response response) {
        super(response.hasResponseBody() ? response.getResponseBody() : String.valueOf(response.getStatusCode()));
        this.response = response;
    }
}
