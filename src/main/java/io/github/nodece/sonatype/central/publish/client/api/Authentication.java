/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.client.api;

import java.util.Map;

public interface Authentication {
    Map<String, String> getHeaders();
}
