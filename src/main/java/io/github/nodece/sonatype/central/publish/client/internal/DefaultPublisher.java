/*
 * SPDX-FileCopyrightText: Copyright 2025 the original author or authors.
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.nodece.sonatype.central.publish.client.internal;

import io.github.nodece.sonatype.central.publish.client.api.Authentication;
import io.github.nodece.sonatype.central.publish.client.api.DeploymentStatus;
import io.github.nodece.sonatype.central.publish.client.api.Publisher;
import io.github.nodece.sonatype.central.publish.client.api.PublisherConfig;
import io.github.nodece.sonatype.central.publish.client.api.PublishingType;
import io.github.nodece.sonatype.central.publish.util.FutureUtils;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.inject.Named;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig.Builder;
import org.asynchttpclient.Response;
import org.asynchttpclient.request.body.multipart.InputStreamPart;
import org.asynchttpclient.util.HttpConstants.Methods;

@Named
public class DefaultPublisher implements Publisher {
    private AsyncHttpClient asyncHttpClient;
    private PublisherConfig publisherConfig;

    public static URI join(URI baseUri, String pathSegment, String query) throws URISyntaxException {
        URI resolvedUri = baseUri.resolve(pathSegment);
        return new URI(
                resolvedUri.getScheme(),
                resolvedUri.getAuthority(),
                resolvedUri.getPath(),
                query,
                resolvedUri.getFragment());
    }

    public static String mapToQueryString(Map<String, String> map) {
        StringBuilder query = new StringBuilder();
        map.forEach((key, value) -> {
            if (query.length() > 0) {
                query.append("&");
            }
            query.append(key).append("=").append(value);
        });
        return query.toString();
    }

    private CompletableFuture<Response> request(
            String method, URI uri, Consumer<BoundRequestBuilder> requestBuilderConsumer) {
        BoundRequestBuilder prepare = asyncHttpClient.prepare(method, uri.toString());
        Authentication authentication = publisherConfig.getAuthentication();
        if (authentication != null) {
            Map<String, String> headers = authentication.getHeaders();
            if (headers != null) {
                headers.forEach(prepare::addHeader);
            }
        }
        requestBuilderConsumer.accept(prepare);
        return prepare.execute().toCompletableFuture().thenCompose(n -> {
            CompletableFuture<Response> future = new CompletableFuture<>();
            if (n.getStatusCode() >= 200 && n.getStatusCode() < 300) {
                future.complete(n);
            } else {
                future.completeExceptionally(new HttpResponseException(n));
            }
            return future;
        });
    }

    @Override
    public CompletableFuture<Void> initialize(PublisherConfig config) {
        Builder builder = new Builder();
        builder.setMaxConnections(1);
        builder.setConnectTimeout(Duration.ofMinutes(1));
        builder.setRequestTimeout(Duration.ofMinutes(30));
        builder.setReadTimeout(Duration.ofMinutes(30));
        asyncHttpClient = new DefaultAsyncHttpClient(builder.build());
        publisherConfig = config;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> upload(
            String deploymentName, PublishingType publishingType, String filename, InputStream inputStream) {
        Map<String, String> query = new HashMap<>();
        if (deploymentName != null) {
            query.put("name", deploymentName);
        }
        query.put("publishingType", publishingType.name());
        try {
            URI uri = join(publisherConfig.getUri(), "publisher/upload", mapToQueryString(query));
            return request(Methods.POST, uri, n -> {
                        n.addBodyPart(new InputStreamPart("bundle", inputStream, filename));
                    })
                    .thenApply(Response::getResponseBody);
        } catch (Exception e) {
            return FutureUtils.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Void> publish(String deploymentId) {
        return null;
    }

    @Override
    public CompletableFuture<DeploymentStatus> status(String deploymentId) {
        Map<String, String> query = new HashMap<>();
        query.put("id", deploymentId);
        try {
            URI uri = join(publisherConfig.getUri(), "publisher/status", mapToQueryString(query));
            return request(Methods.GET, uri, __ -> {})
                    .thenCompose(n -> CompletableFuture.completedFuture(new DeploymentStatus()));
        } catch (Exception e) {
            return FutureUtils.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            asyncHttpClient.close();
            future.complete(null);
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
