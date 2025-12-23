package com.wilhg.lyocell;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ConcurrentHttpClient {

  private final HttpClient httpClient;

  public ConcurrentHttpClient() {
    this.httpClient = HttpClient.newBuilder()
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build();
  }

  public CompletableFuture<HttpResponse<String>> query(HttpRequest request) {
    try {
      String urlWithParams = buildUrlWithQueryParams(request.url(), request.queryParams());

      var httpRequestBuilder = java.net.http.HttpRequest.newBuilder()
          .uri(URI.create(urlWithParams))
          .timeout(Duration.ofMillis(request.timeout()));

      // Add headers
      if (request.headers() != null) {
        request.headers().forEach(httpRequestBuilder::header);
      }

      // Add body based on HTTP method
      switch (request.method().toUpperCase()) {
        case "GET" -> httpRequestBuilder.GET();
        case "POST" -> httpRequestBuilder.POST(
            request.body() != null ? BodyPublishers.ofString(request.body()) : BodyPublishers.noBody()
        );
        case "PUT" -> httpRequestBuilder.PUT(
            request.body() != null ? BodyPublishers.ofString(request.body()) : BodyPublishers.noBody()
        );
        case "DELETE" -> httpRequestBuilder.DELETE();
        default -> throw new IllegalArgumentException("Unsupported HTTP method: " + request.method());
      }

      return httpClient.sendAsync(httpRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

  private String buildUrlWithQueryParams(String url, Map<String, String> queryParams) {
    // Enhanced null check pattern
    if (queryParams == null || queryParams.isEmpty()) {
      return url;
    }

    String queryString = queryParams.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(Collectors.joining("&"));

    // Use switch expression for cleaner conditional logic
    return switch (url.contains("?")) {
      case true -> url + "&" + queryString;
      case false -> url + "?" + queryString;
    };
  }
}
