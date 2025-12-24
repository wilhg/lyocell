package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpVerbsTest {
    @Test
    public void testHttpVerbs() throws Exception {
        String script = """
            import http from 'lyocell/http';
            import { check } from 'lyocell';
            
            export default function() {
                // Use a real URL that supports these methods or a mock if available
                // For unit test, we can just check if the methods are defined
                check(http, {
                    'get is defined': (h) => typeof h.get === 'function',
                    'post is defined': (h) => typeof h.post === 'function',
                    'put is defined': (h) => typeof h.put === 'function',
                    'patch is defined': (h) => typeof h.patch === 'function',
                    'del is defined': (h) => typeof h.del === 'function',
                });
            }
            """;
        Path scriptPath = Files.createTempFile("test-verbs", ".js");
        Files.writeString(scriptPath, script);
        
        MetricsCollector collector = new MetricsCollector();
        TestEngine testEngine = new TestEngine(Collections.emptyList());
        
        try (JsEngine engine = new JsEngine(Collections.emptyMap(), collector, testEngine)) {
            engine.runScript(scriptPath);
            engine.executeDefault(null);
        }
        
        assertTrue(collector.getCounterValue("checks.pass") >= 5);
        assertTrue(collector.getCounterValue("checks.fail") == 0);
    }
}