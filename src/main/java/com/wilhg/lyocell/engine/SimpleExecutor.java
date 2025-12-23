package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

/**
 * A simple executor that runs a fixed number of VUs, each performing a fixed number of iterations.
 * This corresponds to a mix of constant-vus and per-vu-iterations (depending on how config is used).
 */
public class SimpleExecutor implements WorkloadExecutor {
    @Override
    public void execute(
        Path scriptPath,
        TestConfig config,
        Map<String, Object> extraBindings,
        String setupDataJson,
        MetricsCollector metricsCollector
    ) throws InterruptedException, ExecutionException {
        try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
            for (int i = 0; i < config.vus(); i++) {
                int vuId = i;
                scope.fork(() -> {
                    new VuWorker(
                        vuId, 
                        scriptPath, 
                        extraBindings, 
                        setupDataJson, 
                        metricsCollector, 
                        config.iterations()
                    ).run();
                    return null;
                });
            }
            scope.join();
        }
    }
}
