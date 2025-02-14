/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.client.api;

public enum DeploymentState {
    PENDING,
    VALIDATING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
