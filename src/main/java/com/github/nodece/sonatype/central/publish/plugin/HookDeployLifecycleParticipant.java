/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.github.nodece.sonatype.central.publish.plugin;

import static com.github.nodece.sonatype.central.publish.plugin.Constants.PLUGIN_ARTIFACT_ID;
import static com.github.nodece.sonatype.central.publish.plugin.Constants.PLUGIN_GROUP_ID;

import java.util.List;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Named
@Singleton
public class HookDeployLifecycleParticipant extends AbstractMavenLifecycleParticipant implements LogEnabled {
    private Logger logger;

    public void enableLogging(Logger logger) {
        this.logger = logger;
    }

    private boolean isSkipDeployment(Xpp3Dom root) {
        if (root == null) {
            return false;
        }
        Xpp3Dom skipElement = root.getChild("skip");
        return skipElement != null && "true".equals(skipElement.getValue());
    }

    private boolean isSkipDeployment(Plugin plugin) {
        Xpp3Dom pluginConfig = (Xpp3Dom) plugin.getConfiguration();
        if (isSkipDeployment(pluginConfig)) {
            return true;
        }

        PluginExecution defaultExecution = plugin.getExecutionsAsMap().get("default-deploy");
        return defaultExecution != null && isSkipDeployment((Xpp3Dom) defaultExecution.getConfiguration());
    }

    private void setupPublishExecution(Plugin plugin) {
        PluginExecution execution = new PluginExecution();
        execution.setId("default-deploy");
        execution.getGoals().add("publish");
        execution.setPhase("deploy");
        execution.setConfiguration(plugin.getConfiguration());
        if (plugin.getExecutions().stream().noneMatch(n -> execution.getId().equals(n.getId()))) {
            plugin.addExecution(execution);
        }
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        for (MavenProject project : session.getProjects()) {
            Build build = project.getModel().getBuild();
            if (build == null) {
                continue;
            }

            List<Plugin> plugins = build.getPlugins();
            Plugin mavenDeployPlugin = null;
            Plugin selfDeployPlugin = null;
            for (Plugin plugin : plugins) {
                if ("org.apache.maven.plugins".equals(plugin.getGroupId())
                        && "maven-deploy-plugin".equals(plugin.getArtifactId())) {
                    mavenDeployPlugin = plugin;
                } else if (PLUGIN_GROUP_ID.equals(plugin.getGroupId())
                        && PLUGIN_ARTIFACT_ID.equals(plugin.getArtifactId())) {
                    selfDeployPlugin = plugin;
                }
                if (selfDeployPlugin != null && mavenDeployPlugin != null) {
                    if (isSkipDeployment(mavenDeployPlugin)) {
                        logger.debug("maven-deploy-plugin is skipped. Setting skip=true for " + PLUGIN_ARTIFACT_ID);
                        Xpp3Dom configuration = (Xpp3Dom) selfDeployPlugin.getConfiguration();
                        if (configuration == null) {
                            configuration = new Xpp3Dom("configuration");
                            selfDeployPlugin.setConfiguration(configuration);
                        }
                        Xpp3Dom skip = configuration.getChild("skip");
                        if (skip == null) {
                            skip = new Xpp3Dom("skip");
                            configuration.addChild(skip);
                        }
                        skip.setValue("true");
                    }
                    mavenDeployPlugin.getExecutions().clear();
                }
            }
            if (selfDeployPlugin != null) {
                setupPublishExecution(selfDeployPlugin);
            }
        }
    }
}
