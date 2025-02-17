/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.client.api;

import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class DeploymentStatus {
    private String deploymentId;
    private String deploymentName;
    private DeploymentState deploymentState;
    private List<String> purls;
    private Map<String, List<String>> errors;
}
