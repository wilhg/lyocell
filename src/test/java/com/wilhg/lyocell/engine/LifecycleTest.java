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

class LifecycleTest {

    @TempDir
    Path tempDir;

    @Test
    void testLifecycleExecution() throws Exception {
        Path script = tempDir.resolve("lifecycle_test.js");
        Files.writeString(script, """
            const helper = globalThis.TestHelper;
            
            // Init code (runs once per VU)
            helper.recordInit();

            export function setup() {
                helper.recordSetup();
                return { myData: 123 };
            }

            export default function(data) {
                helper.recordDefault(data.myData);
            }

            export function teardown(data) {
                helper.recordTeardown(data.myData);
            }
            """);

        TestLifecycleHelper helper = new TestLifecycleHelper();
        TestEngine testEngine = new TestEngine(Map.of("TestHelper", helper), Collections.emptyList());
        
        // 5 VUs, 1 iteration each
        TestConfig config = new TestConfig(5, 1, null);
        
        testEngine.run(script, config);

        assertEquals(6, helper.initCount.get(), "Init should run once per VU + once for Setup");
        assertEquals(1, helper.setupCount.get(), "Setup should run exactly once");
        assertEquals(5, helper.defaultCount.get(), "Default should run once per VU");
        assertEquals(1, helper.teardownCount.get(), "Teardown should run exactly once");
        
        // Verify data passing
        assertTrue(helper.dataReceivedCorrectly.get(), "Data from setup should be passed to default/teardown");
    }

    public static class TestLifecycleHelper {
        public final AtomicInteger initCount = new AtomicInteger(0);
        public final AtomicInteger setupCount = new AtomicInteger(0);
        public final AtomicInteger defaultCount = new AtomicInteger(0);
        public final AtomicInteger teardownCount = new AtomicInteger(0);
        public final java.util.concurrent.atomic.AtomicBoolean dataReceivedCorrectly = new java.util.concurrent.atomic.AtomicBoolean(true);

        @HostAccess.Export public void recordInit() { initCount.incrementAndGet(); }
        @HostAccess.Export public void recordSetup() { setupCount.incrementAndGet(); }
        
        @HostAccess.Export public void recordDefault(Object data) { 
            defaultCount.incrementAndGet();
            if (!Integer.valueOf(123).equals(data)) dataReceivedCorrectly.set(false);
        }
        
        @HostAccess.Export public void recordTeardown(Object data) { 
            teardownCount.incrementAndGet();
            if (!Integer.valueOf(123).equals(data)) dataReceivedCorrectly.set(false);
        }
    }
}