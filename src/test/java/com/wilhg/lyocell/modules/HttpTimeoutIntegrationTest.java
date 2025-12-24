package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.OutputConfig;
import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("integration")
public class HttpTimeoutIntegrationTest {

    @Container
    public static GenericContainer<?> httpbun = new GenericContainer<>("sharat87/httpbun:latest")
            .withExposedPorts(80);

    @Test
    void testHttpTimeout() throws Exception {
        String baseUrl = "http://" + httpbun.getHost() + ":" + httpbun.getMappedPort(80);
        
        // This script will attempt a GET request to /delay/3 with a 1s timeout
        String scriptContent = """
            import http from 'lyocell/http';
            import { check } from 'lyocell';

            export default function() {
                const res = http.get('%s/delay/3', { timeout: '1s' });
                
                check(res, {
                    'status is 0 (timed out)': (r) => r.status === 0,
                    'error message contains timed out': (r) => r.body.toLowerCase().includes('timed out') || r.body.toLowerCase().includes('timeout')
                });
            }
            """.formatted(baseUrl);

        Path scriptPath = Files.createTempFile("timeout-test", ".js");
        Files.writeString(scriptPath, scriptContent);

        MetricsCollector collector = new MetricsCollector();
        TestEngine engine = new TestEngine(Collections.emptyList());
        
        // We use the same collector for the engine to verify metrics later
        engine.run(scriptPath, new TestConfig(1, 1, null));

        // In Lyocell, checks record their results to the collector
        long passed = engine.getMetricsCollector().getCounterValue("checks.pass");
        long failed = engine.getMetricsCollector().getCounterValue("checks.fail");

        assertEquals(1, passed, "The check should pass (the request should have timed out)");
        assertEquals(0, failed, "No checks should fail");
    }

    @Test
    void testHttpNoTimeout() throws Exception {
        String baseUrl = "http://" + httpbun.getHost() + ":" + httpbun.getMappedPort(80);
        
        // This script will attempt a GET request to /delay/1 with a 3s timeout (should succeed)
        String scriptContent = """
            import http from 'lyocell/http';
            import { check } from 'lyocell';

            export default function() {
                const res = http.get('%s/delay/1', { timeout: '3s' });
                
                check(res, {
                    'status is 200': (r) => r.status === 200
                });
            }
            """.formatted(baseUrl);

        Path scriptPath = Files.createTempFile("no-timeout-test", ".js");
        Files.writeString(scriptPath, scriptContent);

        TestEngine engine = new TestEngine(Collections.emptyList());
        engine.run(scriptPath, new TestConfig(1, 1, null));

        long passed = engine.getMetricsCollector().getCounterValue("checks.pass");
        assertEquals(1, passed, "The check should pass (the request should NOT have timed out)");
    }
}

