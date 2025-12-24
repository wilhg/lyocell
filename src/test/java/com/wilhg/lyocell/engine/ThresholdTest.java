package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ThresholdTest {

    @TempDir
    Path tempDir;

    @Test
    void testThresholdFailure() throws Exception {
        Path script = tempDir.resolve("threshold_fail.js");
        Files.writeString(script, """
            import { check } from 'lyocell';
            export const options = {
                thresholds: {
                    'checks': ['rate<0.1'], // Allow < 10% failure
                },
            };
            export default function() {
                // 1 pass, 1 fail -> 50% failure rate
                check(true, { 'pass': (v) => v });
                check(false, { 'fail': (v) => v });
            }
            """);

        TestEngine testEngine = new TestEngine(Collections.emptyList());
        
        // Use a wrapper/exception to assert failure
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            testEngine.run(script, new TestConfig(1, 1, null));
        });
        
        assertTrue(exception.getMessage().contains("Thresholds failed"), "Should throw on threshold failure");
    }
}