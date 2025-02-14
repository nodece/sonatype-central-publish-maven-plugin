/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.github.nodece.sonatype.central.publish.plugin;

import com.github.nodece.sonatype.central.publish.client.api.Authentication;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Builder;
import org.apache.maven.settings.Server;

@Builder
public class DefaultAuthentication implements Authentication {
    private final Map<String, String> headers = new ConcurrentHashMap<>();

    private DefaultAuthentication() {}

    public static Authentication create(Server server, String username, String password, String token) {
        DefaultAuthentication authentication = new DefaultAuthentication();
        if (token != null) {
            authentication.addTokenAuthHeader(token);
            return authentication;
        }
        if (username != null && password != null) {
            authentication.addTokenAuthHeader(username, password);
            return authentication;
        }
        if (server != null) {
            String u = server.getUsername();
            String p = server.getPassword();
            if (u != null && p != null) {
                authentication.addTokenAuthHeader(server.getUsername(), server.getPassword());
            }
        }
        return authentication;
    }

    private void addAuthHeader(String value) {
        headers.put("Authorization", value);
    }

    private void addTokenAuthHeader(String token) {
        addAuthHeader("Bearer " + token);
    }

    private void addTokenAuthHeader(String username, String password) {
        addTokenAuthHeader(java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }
}
