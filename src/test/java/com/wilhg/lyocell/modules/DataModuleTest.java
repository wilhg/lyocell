package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataModuleTest {
    @Test
    public void testSharedArray() throws Exception {
        String script = """
            import { SharedArray } from 'k6/data';
            import { check } from 'k6';
            
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
        
        // VU 1
        try (JsEngine engine = new JsEngine(Map.of(), collector)) {
            engine.runScript(scriptPath);
            engine.executeDefault(null);
        }
        
        // VU 2 (Should use cached data)
        try (JsEngine engine = new JsEngine(Map.of(), collector)) {
            engine.runScript(scriptPath);
            engine.executeDefault(null);
        }
        
        assertEquals(2, collector.getCounterValue("checks.pass"));
    }
}
