package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Interface for different k6 executor strategies.
 */
public interface WorkloadExecutor {
    /**
     * Executes the workload.
     * 
     * @param scriptPath Path to the script to execute.
     * @param config The test configuration.
     * @param extraBindings Extra JS bindings.
     * @param setupDataJson JSON representation of setup() data.
     * @param metricsCollector Collector for metrics.
     * @throws InterruptedException if interrupted.
     * @throws ExecutionException if execution fails.
     */
    void execute(
        Path scriptPath,
        TestConfig config,
        Map<String, Object> extraBindings,
        String setupDataJson,
        MetricsCollector metricsCollector
    ) throws InterruptedException, ExecutionException;
}
