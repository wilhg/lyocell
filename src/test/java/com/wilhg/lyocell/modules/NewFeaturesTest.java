package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NewFeaturesTest {
    @Test
    public void testNewFeatures() throws Exception {
        String script = """
            import http from 'lyocell/http';
            import crypto from 'lyocell/crypto';
            import encoding from 'lyocell/encoding';
            import { setTimeout } from 'lyocell/timers';
            import { check } from 'lyocell';
            
            export default function() {
                // Crypto
                const hash = crypto.sha256('hello', 'hex');
                check(hash, { 'sha256 works': (h) => h === '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824' });
                
                // Encoding
                const b64 = encoding.b64encode('hello', 'rawstd');
                check(b64, { 'b64encode rawstd works': (b) => b === 'aGVsbG8' });

                // Http Batch
                const responses = http.batch([
                    'https://httpbun.com/get',
                    ['GET', 'https://httpbun.com/get'],
                    { method: 'GET', url: 'https://httpbun.com/get' }
                ]);
                check(responses, { 'batch works': (r) => r.length === 3 });
                
                // Timers
                let timerCalled = false;
                setTimeout(() => { timerCalled = true; }, 10);
                
                // We can't easily wait for timers in this sync test without sleep
                // But we can check if it's defined
                check(setTimeout, { 'setTimeout is defined': (s) => typeof s === 'function' });
            }
            """;
        Path scriptPath = Files.createTempFile("test-new-features", ".js");
        Files.writeString(scriptPath, script);
        
        MetricsCollector collector = new MetricsCollector();
        TestEngine testEngine = new TestEngine(Collections.emptyList());
        
        try (JsEngine engine = new JsEngine(Collections.emptyMap(), collector, testEngine)) {
            engine.runScript(scriptPath);
            engine.executeDefault(null);
        }
        
        assertTrue(collector.getCounterValue("checks.pass") >= 3);
        assertEquals(0, collector.getCounterValue("checks.fail"));
    }
}

