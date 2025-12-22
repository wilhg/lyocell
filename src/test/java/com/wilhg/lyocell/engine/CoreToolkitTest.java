package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CoreToolkitTest {

    @TempDir
    Path tempDir;

    @Test
    void testCheckMetric() throws Exception {
        Path script = tempDir.resolve("check_test.js");
        Files.writeString(script, """
            import { check } from 'k6';
            export default function() {
                check(200, {
                    'is 200': (s) => s === 200,
                    'is not 400': (s) => s !== 400,
                });
                check(500, {
                    'is 200 fails': (s) => s === 200,
                });
            }
            """);

        TestEngine engine = new TestEngine();
        engine.run(script, new TestConfig(1, 1, null));

        MetricsCollector collector = engine.getMetricsCollector();
        // In k6, 'checks' is a rate metric. For now, we'll track total passes/fails.
        // We'll verify that the collector saw these.
        assertEquals(2, collector.getCounterValue("checks.pass"));
        assertEquals(1, collector.getCounterValue("checks.fail"));
    }

    @Test
    void testGroupExecution() throws Exception {
        Path script = tempDir.resolve("group_test.js");
        Files.writeString(script, """
            import { group } from 'k6';
            import { Counter } from 'k6/metrics';
            const c = new Counter('group_counter');
            export default function() {
                group('my-group', function() {
                    c.add(1);
                });
            }
            """);

        TestEngine engine = new TestEngine();
        engine.run(script, new TestConfig(1, 1, null));

        MetricsCollector collector = engine.getMetricsCollector();
        assertEquals(1, collector.getCounterValue("group_counter"));
    }
}
