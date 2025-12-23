package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.engine.scenario.Scenario;
import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Interface for different k6 executor strategies.
 */
public interface WorkloadExecutor {
    /**
     * Executes the workload for a specific scenario.
     * 
     * @param scenario The scenario to execute.
     * @param scriptPath Path to the script to execute.
     * @param extraBindings Extra JS bindings.
     * @param setupDataJson JSON representation of setup() data.
     * @param metricsCollector Collector for metrics.
     * @param testEngine The test engine.
     * @throws InterruptedException if interrupted.
     * @throws ExecutionException if execution fails.
     */
    void execute(
        Scenario scenario,
        Path scriptPath,
        Map<String, Object> extraBindings,
        String setupDataJson,
        MetricsCollector metricsCollector,
        TestEngine testEngine
    ) throws InterruptedException, ExecutionException;
}
