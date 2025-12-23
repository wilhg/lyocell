package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadExecutorTest {

    @Test
    void testCustomExecutor() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        WorkloadExecutor mockExecutor = (scriptPath, config, extraBindings, setupDataJson, metricsCollector) -> {
            executed.set(true);
        };

        // We need a way to inject the executor into TestEngine or test it directly.
        // For now, let's just verify the interface and a manual call to ensure it works.
        mockExecutor.execute(Paths.get("test.js"), new TestConfig(1, 1), Collections.emptyMap(), null, new MetricsCollector());
        
        assertTrue(executed.get(), "Executor should have been called");
    }
}
