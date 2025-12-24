package com.wilhg.lyocell.metrics;

import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsMetricsIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testJsMetricsAggregation() throws Exception {
        Path script = tempDir.resolve("metrics_test.js");
        Files.writeString(script, """
            import { Counter, Trend } from 'lyocell/metrics';
            const c = new Counter('js_counter');
            const t = new Trend('js_trend');
            export default function() {
                c.add(1);
                t.add(100);
            }
            """);

        TestEngine testEngine = new TestEngine(Collections.emptyList());
        // 5 VUs, 1 iteration
        // Corrected: Use the 'testEngine' instance declared above
        testEngine.run(script, new TestConfig(5, 1, null));

        MetricsCollector collector = testEngine.getMetricsCollector();
        assertEquals(5, collector.getCounterValue("js_counter"));
        assertEquals(100, collector.getTrendSummary("js_trend").avg());
    }
}
