package com.wilhg.lyocell;

import com.wilhg.lyocell.engine.OutputConfig;
import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

public class Phase7IntegrationTest {
    @Test
    public void testFullPhase7Features() throws Exception {
        String script = """
            import http from 'lyocell/http';
            import { check, sleep, fail } from 'lyocell';
            import exec from 'lyocell/execution';
            import encoding from 'lyocell/encoding';
            import crypto from 'lyocell/crypto';
            import { SharedArray } from 'lyocell/data';
            import { Counter } from 'lyocell/metrics';
            
            const data = new SharedArray('users', () => [
                { id: 1, name: 'Alice' },
                { id: 2, name: 'Bob' }
            ]);
            
            const myCount = new Counter('custom_count');
            
            export default function() {
                // 1. Execution Info
                const vuId = exec.vu.idInTest;
                const iter = exec.vu.iterationInInstance;
                
                // 2. Encoding & Crypto
                const b64 = encoding.b64encode('lyocell');
                const hash = crypto.sha256('lyocell', 'hex');
                
                // 3. Shared Data
                const user = data[0];
                
                // 4. Metrics
                myCount.add(1);
                
                // 5. Checks
                check(null, {
                    'encoding works': () => b64 === 'bHlvY2VsbA==',
                    'crypto works': () => typeof hash === 'string',
                    'shared data works': () => user.name === 'Alice',
                    'vu id is valid': () => vuId >= 0,
                });
                
                if (iter > 5) {
                    exec.test.abort();
                }
            }
            """;
        Path scriptPath = Files.createTempFile("phase7-full", ".js");
        Files.writeString(scriptPath, script);
        
        TestEngine engine = new TestEngine(Collections.emptyList());
        TestConfig config = new TestConfig(2, 20, java.time.Duration.ofSeconds(10), java.util.Collections.emptyList());
        
        engine.run(scriptPath, config);
        
        MetricsCollector collector = engine.getMetricsCollector();
        assertTrue(collector.getCounterValue("checks.pass") > 0);
        assertTrue(collector.getCounterValue("iterations") <= 20, "Should have stopped due to abort");
        assertEquals(1, collector.getCounterValue("custom_count") / collector.getCounterValue("iterations"), "Custom count should match iterations");
    }

    @Test
    public void testFailFunction() throws Exception {
        String script = """
            import { fail } from 'lyocell';
            export default function() {
                fail('explicit failure');
            }
            """;
        Path scriptPath = Files.createTempFile("test-fail", ".js");
        Files.writeString(scriptPath, script);
        
        TestEngine engine = new TestEngine(Collections.emptyList());
        TestConfig config = new TestConfig(1, 1, java.time.Duration.ofSeconds(1), java.util.Collections.emptyList());
        
        try {
            engine.run(scriptPath, config);
        } catch (Exception e) {
            // Expected
        }
        
        MetricsCollector collector = engine.getMetricsCollector();
        assertEquals(1, collector.getCounterValue("iterations_failed"));
    }
}
