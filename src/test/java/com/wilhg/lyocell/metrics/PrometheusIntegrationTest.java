package com.wilhg.lyocell.metrics;

import com.wilhg.lyocell.engine.OutputConfig;
import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusIntegrationTest {

    @Test
    void testPrometheusScrapeEndpoint() throws Exception {
        int port = 9091; // Use a different port to avoid conflict
        Path script = Files.createTempFile("test-prom", ".js");
        Files.writeString(script, "import { Counter } from 'k6/metrics'; const c = new Counter('test_counter'); export default function() { c.add(1); }");

        TestEngine engine = new TestEngine();
        TestConfig config = new TestConfig(1, 10, null, List.of(
            new OutputConfig("prometheus", String.valueOf(port))
        ));

        // Start engine
        engine.run(script, config);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/metrics"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Prometheus response: " + response.body());
        assertTrue(response.statusCode() == 200);
        assertTrue(response.body().contains("test_counter")); 

        Files.deleteIfExists(script);
    }
}
