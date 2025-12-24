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

public class CryptoEncodingTest {
    @Test
    public void testCryptoAndEncoding() throws Exception {
        String script = """
            import encoding from 'lyocell/encoding';
            import crypto from 'lyocell/crypto';
            import { check } from 'lyocell';
            
            export default function() {
                const encoded = encoding.b64encode('hello');
                const decoded = encoding.b64decode(encoded);
                
                const hash = crypto.sha256('hello', 'hex');
                // sha256 of 'hello' is 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
                
                const hmac = crypto.hmac('sha256', 'secret', 'hello', 'hex');
                
                check(null, {
                    'b64encode works': () => encoded === 'aGVsbG8=',
                    'b64decode works': () => decoded === 'hello',
                    'sha256 works': () => hash === '2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824',
                    'hmac works': () => typeof hmac === 'string' && hmac.length === 64,
                });
            }
            """;
        Path scriptPath = Files.createTempFile("test-crypto", ".js");
        Files.writeString(scriptPath, script);
        
        MetricsCollector collector = new MetricsCollector();
        TestEngine testEngine = new TestEngine(Collections.emptyList());
        
        try (JsEngine engine = new JsEngine(Collections.emptyMap(), collector, testEngine)) {
            engine.runScript(scriptPath);
            engine.executeDefault(null);
        }
        
        assertTrue(collector.getCounterValue("checks.pass") == 1);
        assertTrue(collector.getCounterValue("checks.fail") == 0);
    }
}