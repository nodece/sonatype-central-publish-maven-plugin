/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.plugin;

public class Constants {
    public static final String PLUGIN_GROUP_ID = "io.github.nodece";
    public static final String PLUGIN_ARTIFACT_ID = "sonatype-central-publish-maven-plugin";
    public static final String PLUGIN_NOTATION = PLUGIN_GROUP_ID + ":" + PLUGIN_ARTIFACT_ID;
    public static final String CENTRAL_REPOSITORY_URL = "https://central.sonatype.com/api/v1/";
    public static final String CENTRAL_SNAPSHOT_REPOSITORY_URL =
            "https://central.sonatype.com/repository/maven-snapshots/";
}
