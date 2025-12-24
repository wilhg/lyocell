package com.wilhg.lyocell;

import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Testcontainers
@Tag("integration")
class K6ExamplesIntegrationTest {

    @Container
    static GenericContainer<?> httpbun = new GenericContainer<>("sharat87/httpbun:latest")
            .withExposedPorts(80);

    private void runTest(String scriptPath) {
        String baseUrl = "http://" + httpbun.getHost() + ":" + httpbun.getMappedPort(80);
        Path script = Paths.get(scriptPath);
        
        TestEngine engine = new TestEngine(Map.of(
            "__ENV", Map.of("BASE_URL", baseUrl)
        ));
        
        assertDoesNotThrow(() -> 
            engine.run(script, new TestConfig(1, 1, null))
        );
    }

    @Test void testHttpGet() { runTest("examples/k6/http_get.js"); }
    @Test void testHttpVerbs() { runTest("examples/k6/http_verbs.js"); }
    @Test void testStages() { runTest("examples/k6/stages.js"); }
    @Test void testThresholds() { runTest("examples/k6/thresholds.js"); }
    @Test void testCustomMetrics() { runTest("examples/k6/custom_metrics.js"); }
    @Test void testJson() { runTest("examples/k6/json.js"); }
    @Test void testCrypto() { runTest("examples/k6/crypto.js"); }
}
