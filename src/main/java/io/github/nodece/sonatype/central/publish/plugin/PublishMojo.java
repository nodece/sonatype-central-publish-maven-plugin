/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.plugin;

import static io.github.nodece.sonatype.central.publish.client.api.DeploymentState.FAILED;
import static io.github.nodece.sonatype.central.publish.client.api.DeploymentState.PUBLISHED;
import static io.github.nodece.sonatype.central.publish.plugin.Constants.CENTRAL_REPOSITORY_URL;
import static io.github.nodece.sonatype.central.publish.plugin.Constants.CENTRAL_SNAPSHOT_REPOSITORY_URL;
import static io.github.nodece.sonatype.central.publish.plugin.Constants.PLUGIN_NOTATION;
import static org.apache.maven.plugins.annotations.LifecyclePhase.DEPLOY;

import io.github.nodece.sonatype.central.publish.client.api.DeploymentState;
import io.github.nodece.sonatype.central.publish.client.api.DeploymentStatus;
import io.github.nodece.sonatype.central.publish.client.api.Publisher;
import io.github.nodece.sonatype.central.publish.client.api.PublisherConfig;
import io.github.nodece.sonatype.central.publish.client.api.PublishingType;
import io.github.nodece.sonatype.central.publish.client.internal.DefaultPublisher;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RemoteRepository.Builder;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;

@Slf4j
@Mojo(name = "publish", defaultPhase = DEPLOY, threadSafe = true, requiresOnline = true)
public class PublishMojo extends AbstractMojo {
    @Inject
    private RepositorySystem repositorySystem;
    @Inject
    private SettingsDecrypter settingsDecrypter;

    @Inject
    @Named(SimpleLocalRepositoryManagerFactory.NAME)
    private LocalRepositoryManagerFactory simpleLocalRepositoryManagerFactory;

    @Parameter(defaultValue = "${plugin}", required = true, readonly = true)
    private PluginDescriptor pluginDescriptor;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Parameter(property = "skip")
    private boolean skip;

    @Parameter(name = "username")
    private String username;

    @Parameter(name = "password")
    private String password;

    @Parameter(name = "token")
    private String token;

    @Parameter(name = "url")
    private URI uri;

    @Parameter(name = "snapshotUrl")
    private URI snapshotUri;

    @Parameter(name = "deploymentName")
    private String deploymentName;

    @Parameter(name = "publishingType", defaultValue = "USER_MANAGED")
    private PublishingType publishingType;

    @Parameter(name = "serverId", defaultValue = "central")
    private String serverId;

    private enum PublishState {
        PENDING,
        SKIPPED
    }

    private void putPublishState(MavenProject project, PublishState state) {
        log.info("Setting state {} for {}", state, project);
        session.getPluginContext(pluginDescriptor, project).put(PublishState.class.getName(), state);
    }

    private boolean hasPendingPublishState(MavenProject project) {
        return PublishState.PENDING.equals(
                session.getPluginContext(pluginDescriptor, project).get(PublishState.class.getName()));
    }

    private PublishState getPublishState(MavenProject project) {
        return (PublishState)
                session.getPluginContext(pluginDescriptor, project).get(PublishState.class.getName());
    }

    private RepositorySystemSession createStagingRepositorySession(Path rootDirectory)
            throws NoLocalRepositoryManagerException {
        RepositorySystemSession repositorySession = session.getRepositorySession();
        DefaultRepositorySystemSession repositorySystemSession = new DefaultRepositorySystemSession(repositorySession);
        Path stagingDirectory = Paths.get(rootDirectory.toString(), "staging");
        repositorySystemSession.setLocalRepositoryManager(simpleLocalRepositoryManagerFactory.newInstance(
                session.getRepositorySession(), new LocalRepository(new File(stagingDirectory.toString()))));
        return repositorySystemSession;
    }

    private RemoteRepository createRemoteRepository(String url) {
        Builder builder = new Builder(serverId, "default", url);
        builder.setAuthentication(
                session.getRepositorySession().getAuthenticationSelector().getAuthentication(builder.build()));
        return builder.build();
    }

    private URI getRepositoryUri(boolean isSnapshot) {
        URI repoUri;
        if (isSnapshot) {
            repoUri = uri;
        } else {
            repoUri = snapshotUri;
        }
        if (repoUri == null) {
            repoUri = isSnapshot ? URI.create(CENTRAL_SNAPSHOT_REPOSITORY_URL) : URI.create(CENTRAL_REPOSITORY_URL);
        }
        return repoUri;
    }

    private RemoteRepository getSnapRemoteRepository() {
        return createRemoteRepository(getRepositoryUri(true).toString());
    }

    private boolean installArtifacts(InstallRequest installRequest, RepositorySystemSession repositorySession)
            throws InstallationException {
        if (installRequest.getArtifacts().isEmpty()) {
            log.info("No artifacts to install");
            return false;
        }
        repositorySystem.install(repositorySession, installRequest);
        return true;
    }

    private Server getServer() {
        Server server = session.getSettings().getServer(serverId);
        if (server!=null){
            DefaultSettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest(server);
            SettingsDecryptionResult decrypt = settingsDecrypter.decrypt(request);
            return decrypt.getServer();
        }
        return null;
    }

    @Override
    public void execute() throws MojoExecutionException {
        PublishState publishState;
        if (skip) {
            publishState = PublishState.SKIPPED;
        } else {
            publishState = PublishState.PENDING;
        }
        putPublishState(session.getCurrentProject(), publishState);
        List<MavenProject> projects = findProjectsWithPlugin();
        if (!areAllProjectsMarked(projects)) {
            return;
        }
        try {
            Path outputDirectory = Files.createTempDirectory(
                    Paths.get(session.getTopLevelProject().getBuild().getDirectory()),
                    "sonatype-central-publisher-maven-plugin-");
            log.info("Output directory: {}", outputDirectory);
            List<MavenProject> pendingProjects =
                    projects.stream().filter(this::hasPendingPublishState).collect(Collectors.toList());
            InstallRequest releaseInstallRequest = new InstallRequest();
            InstallRequest snapshotInstallRequest = new InstallRequest();
            for (MavenProject project : pendingProjects) {
                getAllArtifacts(project).forEach(n -> {
                    if (n.isSnapshot()) {
                        snapshotInstallRequest.addArtifact(n);
                    } else {
                        releaseInstallRequest.addArtifact(n);
                    }
                });
            }

            if (!snapshotInstallRequest.getArtifacts().isEmpty()) {
                DeployRequest deployRequest = new DeployRequest();
                snapshotInstallRequest.getArtifacts().forEach(deployRequest::addArtifact);
                deployRequest.setRepository(getSnapRemoteRepository());
                Exception exception = deploySnapshot(session.getRepositorySession(), deployRequest);
                if (exception != null) {
                    throw exception;
                }
                log.info("Deployed snapshot artifacts to {}", deployRequest.getRepository().getUrl());
            }

            if (!releaseInstallRequest.getArtifacts().isEmpty()) {
                Path bundlePath = Paths.get(outputDirectory.toString(), "bundle.zip");
                RepositorySystemSession stagingRepositorySession = createStagingRepositorySession(outputDirectory);
                if (installArtifacts(releaseInstallRequest, stagingRepositorySession)) {
                    ZipBundle.install(stagingRepositorySession, bundlePath);
                    log.info(
                            "Bundle {} created successfully, size: {}",
                            bundlePath,
                            FileUtils.byteCountToDisplaySize(Files.size(bundlePath)));
                    Publisher publisher = new DefaultPublisher();
                    PublisherConfig publisherConfig = PublisherConfig.builder()
                            .uri(getRepositoryUri(false))
                            .authentication(DefaultAuthentication.create(getServer(), username, password, token))
                            .build();
                    if (log.isDebugEnabled()) {
                        log.debug("Publisher config: {}", publisherConfig);
                    }
                    log.info("Initializing publisher with url: {}", publisherConfig.getUri());
                    publisher.initialize(publisherConfig).get();
                    FileInputStream fileInputStream;
                    fileInputStream = FileUtils.openInputStream(bundlePath.toFile());
                    String finalDeploymentName;
                    if (deploymentName == null) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                        finalDeploymentName =
                                "Deployment-" + LocalDateTime.now().format(formatter);
                    } else {
                        finalDeploymentName = deploymentName;
                    }
                    log.info(
                            "Uploading {} with deployment name: {}, publishing type: {}",
                            bundlePath,
                            finalDeploymentName,
                            publishingType);
                    String deploymentId = publisher
                            .upload(
                                    finalDeploymentName,
                                    publishingType,
                                    bundlePath.getFileName().toString(),
                                    fileInputStream)
                            .get();
                    log.info("Upload completed with deployment id: {}", deploymentId);
                    log.info("Waiting for deployment state to {}", PUBLISHED);
                    boolean success;
                    while (true) {
                        try {
                            DeploymentStatus deploymentStatus =
                                    publisher.status(deploymentId).get();
                            if (log.isDebugEnabled()) {
                                log.debug("Deployment status: {}", deploymentStatus);
                            }
                            Map<String, List<String>> errors = deploymentStatus.getErrors();
                            DeploymentState deploymentState = deploymentStatus.getDeploymentState();
                            if (FAILED.equals(deploymentState) || (errors != null && !errors.isEmpty())) {
                                success = false;
                                break;
                            }
                            if (PUBLISHED.equals(deploymentState)) {
                                success = true;
                                break;
                            }
                        } catch (Exception e) {
                            log.error("Error while checking deployment status", e);
                        }
                        Thread.sleep(3 * 1000);
                    }
                    if (success) {
                        log.info("Published successfully, deployment id: {}", deploymentId);
                    } else {
                        log.error("Failed to publish, deployment id: {}", deploymentId);
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e);
        }
    }

    private List<MavenProject> findProjectsWithPlugin() {
        return session.getProjects().stream()
                .filter((p) -> p.getPlugin(PLUGIN_NOTATION) != null)
                .collect(Collectors.toList());
    }

    private boolean areAllProjectsMarked(List<MavenProject> projects) {
        return projects.stream().allMatch(p -> {
            PublishState publishState = getPublishState(p);
            log.debug("{} state: {}", p, publishState);
            return publishState != null;
        });
    }

    private List<org.eclipse.aether.artifact.Artifact> getAllArtifacts(MavenProject project) {
        org.eclipse.aether.artifact.Artifact pomArtifact = RepositoryUtils.toArtifact(new ProjectArtifact(project));
        org.eclipse.aether.artifact.Artifact projectArtifact = RepositoryUtils.toArtifact(project.getArtifact());
        List<org.eclipse.aether.artifact.Artifact> result = new ArrayList<>();
        result.add(pomArtifact);
        if (!ArtifactIdUtils.equalsVersionlessId(pomArtifact, projectArtifact)) {
            result.add(projectArtifact);
        }
        project.getAttachedArtifacts().forEach(n -> result.add(RepositoryUtils.toArtifact(n)));
        return Collections.unmodifiableList(result);
    }

    private Exception deploySnapshot(RepositorySystemSession repositorySystemSession, DeployRequest deployRequest) {
        AtomicReference<Exception> exceptionAtomicReference = new AtomicReference<>();
        int retry = 0;
        while (retry < 5) {
            try {
                repositorySystem.deploy(repositorySystemSession, deployRequest);
                exceptionAtomicReference.set(null);
                break;
            } catch (Exception e) {
                exceptionAtomicReference.set(e);
            }
            retry++;
        }
        return exceptionAtomicReference.get();
    }
}
