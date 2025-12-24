package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.ExecutionContext;
import com.wilhg.lyocell.metrics.MetricsCollector;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataModuleTest {
    @Test
    public void testSharedArray() throws Exception {
        String script = """
            import { SharedArray } from 'lyocell/data';
            import { check } from 'lyocell';
            
            const data = new SharedArray('test-data', function() {
                return [1, 2, 3];
            });
            
            export default function() {
                check(null, {
                    'shared array has correct data': () => data[0] === 1 && data.length === 3,
                });
            }
            """;
        Path scriptPath = Files.createTempFile("test-data", ".js");
        Files.writeString(scriptPath, script);
        
        MetricsCollector collector = new MetricsCollector();
        
        TestEngine testEngine = new TestEngine(Collections.emptyList());
        
        ScopedValue.where(ExecutionContext.CURRENT, new ExecutionContext(42)).run(() -> {
            // VU 1
            try (JsEngine engine = new JsEngine(Collections.emptyMap(), collector, testEngine)) {
                engine.runScript(scriptPath);
                engine.executeDefault(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            // VU 2 (Should use cached data)
            try (JsEngine engine = new JsEngine(Collections.emptyMap(), collector, testEngine)) {
                engine.runScript(scriptPath);
                engine.executeDefault(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        assertEquals(2L, collector.getCounterValue("checks.pass"));
    }
}