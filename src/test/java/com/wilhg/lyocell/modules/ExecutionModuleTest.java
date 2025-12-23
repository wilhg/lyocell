package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.ExecutionContext;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExecutionModuleTest {
    
    @AfterEach
    void tearDown() {
        ExecutionContext.remove();
    }

    @Test
    public void testExecutionModule() throws Exception {
        String script = """
            import exec from 'k6/execution';
            import { check } from 'k6';
            
            export default function() {
                check(null, {
                    'vu id is correct': () => exec.vu.idInTest === 42,
                    'iteration is tracked': () => exec.vu.iterationInInstance >= 0,
                });
            }
            """;
        Path scriptPath = Files.createTempFile("test-exec", ".js");
        Files.writeString(scriptPath, script);
        
        MetricsCollector collector = new MetricsCollector();
        
        ExecutionContext.set(new ExecutionContext(42));
        try (JsEngine engine = new JsEngine(Map.of(), collector)) {
            engine.runScript(scriptPath);
            engine.executeDefault(null);
        }
        
        assertTrue(collector.getCounterValue("checks.pass") == 2);
    }
}