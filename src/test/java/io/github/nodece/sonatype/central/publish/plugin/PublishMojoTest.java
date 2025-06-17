/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.plugin;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.nodece.sonatype.central.publish.client.api.DeploymentState;
import io.github.nodece.sonatype.central.publish.client.api.DeploymentStatus;
import io.github.nodece.sonatype.central.publish.client.api.Publisher;
import io.github.nodece.sonatype.central.publish.client.internal.HttpResponseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.asynchttpclient.Response;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Slf4j
public class PublishMojoTest {

    @Test
    public void deploymentFailsWithErrors() {
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

        CompletableFuture<DeploymentStatus> futureStatus = PublishMojo.waitPublishStateAsync(publisher, deploymentId);

        assertThat(futureStatus).succeedsWithin(2, TimeUnit.SECONDS).satisfies(result -> {
            assertThat(result.getDeploymentState()).isEqualTo(DeploymentState.FAILED);
            assertThat(result.getErrors()).isEqualTo(finalErrors);
        });
    }

    @Test
    public void deploymentSucceedsWithPublishedState() {
        String deploymentId = "deploymentId-123";

        DeploymentStatus status = DeploymentStatus.builder()
                .deploymentId(deploymentId)
                .deploymentState(DeploymentState.PUBLISHED)
                .purls(Collections.singletonList("pkg:maven/com.example/demo-lib-two@1.0.0"))
                .build();

        Publisher publisher = mock(Publisher.class);
        when(publisher.status(deploymentId)).thenReturn(completedFuture(status));

        CompletableFuture<DeploymentStatus> futureStatus = PublishMojo.waitPublishStateAsync(publisher, deploymentId);

        assertThat(futureStatus).succeedsWithin(2, TimeUnit.SECONDS).satisfies(result -> {
            assertThat(result.getDeploymentState()).isEqualTo(DeploymentState.PUBLISHED);
        });
    }

    @Test(dataProvider = "deploymentTransitions")
    public void deploymentStateTransitions(
            DeploymentState firstState, DeploymentState secondState, DeploymentState expectedFinalState)
            throws Exception {
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

        CompletableFuture<DeploymentStatus> futureStatus = PublishMojo.waitPublishStateAsync(publisher, deploymentId);

        assertThat(futureStatus).succeedsWithin(6, TimeUnit.SECONDS).satisfies(status -> {
            assertThat(status.getDeploymentState()).isEqualTo(expectedFinalState);
        });
    }

    @DataProvider
    public Object[][] deploymentTransitions() {
        return new Object[][] {
            {DeploymentState.PUBLISHING, DeploymentState.PUBLISHED, DeploymentState.PUBLISHED},
            {DeploymentState.PENDING, DeploymentState.FAILED, DeploymentState.FAILED}
        };
    }

    @Test(dataProvider = "httpErrorCodes")
    public void deploymentFailsWithHttpError(int statusCode) {
        String deploymentId = "deploymentId-error-" + statusCode;

        HttpResponseException httpException = mock(HttpResponseException.class);
        Response response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(statusCode);
        when(httpException.getMessage()).thenReturn("HTTP error occurred");
        when(httpException.getResponse()).thenReturn(response);

        Publisher publisher = mock(Publisher.class);
        CompletableFuture<DeploymentStatus> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(httpException);
        when(publisher.status(deploymentId)).thenReturn(failedFuture);

        CompletableFuture<DeploymentStatus> futureStatus = PublishMojo.waitPublishStateAsync(publisher, deploymentId);

        assertThat(futureStatus)
                .failsWithin(3, TimeUnit.SECONDS)
                .withThrowableThat()
                .withCause(httpException);
    }

    @DataProvider
    public Object[][] httpErrorCodes() {
        return new Object[][] {{401}, {403}, {404}};
    }
}
