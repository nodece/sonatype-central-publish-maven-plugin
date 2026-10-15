/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.plugin;

import static io.github.nodece.sonatype.central.publish.client.api.DeploymentState.PUBLISHED;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.nodece.sonatype.central.publish.client.api.DeploymentState;
import io.github.nodece.sonatype.central.publish.client.api.DeploymentStatus;
import io.github.nodece.sonatype.central.publish.client.api.Publisher;
import io.github.nodece.sonatype.central.publish.client.api.PublishingType;
import io.github.nodece.sonatype.central.publish.client.internal.HttpResponseException;
import io.github.nodece.sonatype.central.publish.util.FutureUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Slf4j
public class PublishMojoTest {

    @Test
    public void deploymentFailsWithErrors() throws Throwable {
        String deploymentId = "deploymentId-123";

        Map<String, List<String>> errors = new HashMap<>();
        errors.put("pkg:maven/com.example/demo-lib-one@1.0.0", Collections.singletonList("Project name is missing"));
        errors.put("pkg:maven/com.example/demo-lib-two@1.0.0", Collections.singletonList("Project name is missing"));
        Map<String, List<String>> finalErrors = Collections.unmodifiableMap(errors);

        DeploymentStatus status = DeploymentStatus.builder()
                .deploymentId(deploymentId)
                .errors(errors)
                .deploymentState(DeploymentState.FAILED)
                .build();

        Publisher publisher = mock(Publisher.class);
        when(publisher.status(deploymentId)).thenReturn(completedFuture(status));

        DeploymentStatus deploymentStatus = PublishMojo.waitPublishState(publisher, deploymentId);
        assertThat(deploymentStatus.getDeploymentState()).isEqualTo(DeploymentState.FAILED);
        assertThat(deploymentStatus.getErrors()).isEqualTo(finalErrors);
    }

    @Test
    public void deploymentSucceedsWithPublishedState() throws Throwable {
        String deploymentId = "deploymentId-123";

        DeploymentStatus status = DeploymentStatus.builder()
                .deploymentId(deploymentId)
                .deploymentState(PUBLISHED)
                .purls(Collections.singletonList("pkg:maven/com.example/demo-lib-two@1.0.0"))
                .build();

        Publisher publisher = mock(Publisher.class);
        when(publisher.status(deploymentId)).thenReturn(completedFuture(status));

        DeploymentStatus deploymentStatus = PublishMojo.waitPublishState(publisher, deploymentId);
        assertThat(deploymentStatus.getDeploymentState()).isEqualTo(PUBLISHED);
    }

    @Test(dataProvider = "deploymentTransitions")
    public void deploymentStateTransitions(
            DeploymentState firstState, DeploymentState secondState, DeploymentState expectedFinalState)
            throws Throwable {
        String deploymentId = "test-deployment";

        DeploymentStatus firstStatus = DeploymentStatus.builder()
                .deploymentId(deploymentId)
                .deploymentState(firstState)
                .build();

        DeploymentStatus secondStatus = DeploymentStatus.builder()
                .deploymentId(deploymentId)
                .deploymentState(secondState)
                .build();

        Publisher publisher = mock(Publisher.class);
        when(publisher.status(deploymentId))
                .thenReturn(completedFuture(firstStatus))
                .thenReturn(completedFuture(secondStatus));

        DeploymentStatus deploymentStatus = PublishMojo.waitPublishState(publisher, deploymentId);
        assertThat(deploymentStatus.getDeploymentState()).isEqualTo(expectedFinalState);
    }

    @DataProvider
    public Object[][] deploymentTransitions() {
        return new Object[][] {
            {DeploymentState.PUBLISHING, PUBLISHED, PUBLISHED},
            {DeploymentState.PENDING, DeploymentState.FAILED, DeploymentState.FAILED}
        };
    }

    @Test(dataProvider = "httpErrorCodes")
    public void deploymentFailsWithHttpError(int statusCode) throws Throwable {
        String deploymentId = "deploymentId-error-" + statusCode;

        Response response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(statusCode);
        when(response.getStatusText()).thenReturn("HTTP error occurred");

        Publisher publisher = mock(Publisher.class);
        CompletableFuture<DeploymentStatus> failedFuture = new CompletableFuture<>();
        HttpResponseException httpException = new HttpResponseException(response);
        failedFuture.completeExceptionally(httpException);
        when(publisher.status(deploymentId)).thenReturn(failedFuture);

        assertThatThrownBy(() -> PublishMojo.waitPublishState(publisher, deploymentId))
                .hasCause(httpException);
    }

    @DataProvider
    public Object[][] httpErrorCodes() {
        return new Object[][] {{401}, {403}, {404}};
    }

    @Test
    public void testThreadBlocksUntilPublish() throws Exception {
        String deploymentId = "test-deployment";

        Publisher publisher = mock(Publisher.class);
        AtomicInteger attempts = new AtomicInteger();

        when(publisher.status(deploymentId)).thenAnswer(inv -> {
            int i = attempts.getAndIncrement();
            DeploymentState state = (i < 3) ? DeploymentState.PUBLISHING : DeploymentState.PUBLISHED;
            return CompletableFuture.completedFuture(DeploymentStatus.builder()
                    .deploymentId(deploymentId)
                    .deploymentState(state)
                    .build());
        });

        @Cleanup("shutdownNow")
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<DeploymentStatus> future = executor.submit(() -> {
            try {
                return PublishMojo.waitPublishState(publisher, deploymentId);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(future).succeedsWithin(15, TimeUnit.SECONDS).satisfies(n -> assertThat(n.getDeploymentState())
                .isEqualTo(DeploymentState.PUBLISHED));
    }

    @Test
    public void uploadRetriesOnServerError() throws Throwable {
        Path bundlePath = Paths.get("bundle.zip");

        Response response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(502);
        HttpResponseException serverError = new HttpResponseException(response);

        Publisher publisher = mock(Publisher.class);
        AtomicInteger attempts = new AtomicInteger();
        when(publisher.upload(anyString(), any(PublishingType.class), any(Path.class)))
                .thenAnswer(inv -> attempts.getAndIncrement() == 0
                        ? FutureUtils.<String>failedFuture(serverError)
                        : completedFuture("deployment-id-123"));

        String deploymentId =
                PublishMojo.uploadBundle(publisher, "Deployment-test", PublishingType.USER_MANAGED, bundlePath);

        assertThat(deploymentId).isEqualTo("deployment-id-123");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    public void uploadFailsFastOnClientError() throws Throwable {
        Path bundlePath = Paths.get("bundle.zip");

        Response response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(401);
        HttpResponseException authError = new HttpResponseException(response);

        Publisher publisher = mock(Publisher.class);
        when(publisher.upload(anyString(), any(PublishingType.class), any(Path.class)))
                .thenReturn(FutureUtils.<String>failedFuture(authError));

        assertThatThrownBy(() ->
                        PublishMojo.uploadBundle(publisher, "Deployment-test", PublishingType.USER_MANAGED, bundlePath))
                .hasCause(authError);
        verify(publisher, times(1)).upload(anyString(), any(PublishingType.class), any(Path.class));
    }
}
