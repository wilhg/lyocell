package com.wilhg.lyocell.engine.executor;

import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.engine.VuWorker;
import com.wilhg.lyocell.engine.WorkloadExecutor;
import com.wilhg.lyocell.engine.scenario.PerVuIterationsConfig;
import com.wilhg.lyocell.engine.scenario.Scenario;
import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

public class PerVuIterationsExecutor implements WorkloadExecutor {
    @Override
    public void execute(
        Scenario scenario,
        Path scriptPath,
        Map<String, Object> extraBindings,
        String setupDataJson,
        MetricsCollector metricsCollector,
        TestEngine testEngine
    ) throws InterruptedException, ExecutionException {
        PerVuIterationsConfig config = (PerVuIterationsConfig) scenario.executor();

        // Handle startTime delay
        if (config.startTime() != null && !config.startTime().isZero()) {
            Thread.sleep(config.startTime());
        }

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
                        config.iterations(),
                        testEngine,
                        scenario.exec()
                    ).run();
                    return null;
                });
            }
            scope.join();
        }
    }
}
