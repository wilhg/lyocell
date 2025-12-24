package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.engine.scenario.Scenario;
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
        WorkloadExecutor mockExecutor = (scenario, scriptPath, extraBindings, setupDataJson, metricsCollector, testEngine) -> {
            executed.set(true);
        };

        Scenario dummyScenario = new Scenario("test", new com.wilhg.lyocell.engine.scenario.PerVuIterationsConfig(1, 1, java.time.Duration.ZERO, java.time.Duration.ZERO));
        mockExecutor.execute(dummyScenario, Paths.get("test.js"), Collections.emptyMap(), null, new MetricsCollector(), new TestEngine(Collections.emptyList()));
        
        assertTrue(executed.get(), "Executor should have been called");
    }
}