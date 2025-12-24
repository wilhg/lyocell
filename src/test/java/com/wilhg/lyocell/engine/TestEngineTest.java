package com.wilhg.lyocell.engine;

import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TestEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void testConcurrentVus() throws Exception {
        // Script calls the injected helper inside default function
        Path script = tempDir.resolve("concurrency_test.js");
        Files.writeString(script, """
            const helper = globalThis.TestHelper;
            export default function() {
                helper.increment();
            }
            """);

        AtomicInteger counter = new AtomicInteger(0);
        TestHelper helper = new TestHelper(counter);
        Map<String, Object> extraBindings = Map.of("TestHelper", helper);

        // We need the TestEngine to support passing bindings to VUs
        TestEngine testEngine = new TestEngine(extraBindings, Collections.emptyList());
        
        // 10 VUs, 1 iteration
        TestConfig config = new TestConfig(10, 1, null); 
        
        testEngine.run(script, config);

        assertEquals(10, counter.get(), "Expected 10 VUs to increment the counter");
    }
    
    public static class TestHelper {
        private final AtomicInteger counter;

        public TestHelper(AtomicInteger counter) {
            this.counter = counter;
        }

        @HostAccess.Export
        public void increment() {
            counter.incrementAndGet();
        }
    }
}
