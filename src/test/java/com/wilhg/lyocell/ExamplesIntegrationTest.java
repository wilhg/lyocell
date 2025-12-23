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
class ExamplesIntegrationTest {

    @Container
    static GenericContainer<?> httpbun = new GenericContainer<>("sharat87/httpbun:latest")
            .withExposedPorts(80);

    @Test
    void testBasicGetExample() {
        String baseUrl = "http://" + httpbun.getHost() + ":" + httpbun.getMappedPort(80);
        Path script = Paths.get("examples/basic-get.js");
        
        TestEngine engine = new TestEngine(Map.of(
            "__ENV", Map.of("BASE_URL", baseUrl)
        ));
        
        assertDoesNotThrow(() -> 
            engine.run(script, new TestConfig(1, 1, null))
        );
    }

    @Test
    void testPostJsonExample() {
        String baseUrl = "http://" + httpbun.getHost() + ":" + httpbun.getMappedPort(80);
        Path script = Paths.get("examples/post-json.js");

        TestEngine engine = new TestEngine(Map.of(
            "__ENV", Map.of("BASE_URL", baseUrl)
        ));

        assertDoesNotThrow(() -> 
            engine.run(script, new TestConfig(1, 1, null))
        );
    }
}
