package com.wilhg.lyocell.metrics;

import com.wilhg.lyocell.engine.OutputConfig;
import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PrometheusIntegrationTest {

    @Container
    static GenericContainer<?> pushgateway = new GenericContainer<>("prom/pushgateway:latest")
            .withExposedPorts(9091);

    @Test
    void testPrometheusPush() throws Exception {
        String pushUrl = "http://" + pushgateway.getHost() + ":" + pushgateway.getMappedPort(9091);
        
        Path script = Files.createTempFile("test-prom-push", ".js");
        Files.writeString(script, "import { Counter } from 'lyocell/metrics'; const c = new Counter('test_push_counter'); export default function() { c.add(1); }");

        TestEngine engine = new TestEngine();
        TestConfig config = new TestConfig(1, 10, null, List.of(
            new OutputConfig("prometheus", pushUrl)
        ));

        // Start engine
        engine.run(script, config);

        // Verify metrics are in Pushgateway
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pushUrl + "/metrics"))
                .GET()
                .build();

        // Give it a moment for the async push to happen
        Thread.sleep(6000); 

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Pushgateway response: " + response.body());
        assertTrue(response.statusCode() == 200);
        assertTrue(response.body().contains("test_push_counter"));

        Files.deleteIfExists(script);
    }
}
