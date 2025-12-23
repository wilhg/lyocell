package com.wilhg.lyocell.metrics;

import io.micrometer.core.ipc.http.HttpSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class JdkHttpSender implements HttpSender {
    private final HttpClient client;

    public JdkHttpSender() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public Response send(Request request) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(request.getUrl().toURI())
                .timeout(Duration.ofSeconds(10));

        request.getRequestHeaders().forEach(builder::header);

        Method method = request.getMethod();
        if (method == Method.POST) {
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(request.getEntity()));
        } else if (method == Method.PUT) {
            builder.PUT(HttpRequest.BodyPublishers.ofByteArray(request.getEntity()));
        } else if (method == Method.GET) {
            builder.GET();
        }

        HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

        return new Response(response.statusCode(), new String(response.body()));
    }
}
