package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpModule {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @HostAccess.Export
    public HttpResponseWrapper get(String url, Value params) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            System.out.println("[Http] Sending GET to " + url);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return new HttpResponseWrapper(response);
        } catch (Exception e) {
            System.err.println("[Http] Error: " + e.getMessage());
            return new HttpResponseWrapper(e.getMessage());
        }
    }

    public static class HttpResponseWrapper {
        @HostAccess.Export public final int status;
        @HostAccess.Export public final String body;

        public HttpResponseWrapper(HttpResponse<String> response) {
            this.status = response.statusCode();
            this.body = response.body();
        }

        public HttpResponseWrapper(String error) {
            this.status = 0;
            this.body = error;
        }
    }
}
