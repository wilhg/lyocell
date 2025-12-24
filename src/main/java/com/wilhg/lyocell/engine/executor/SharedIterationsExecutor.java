package com.wilhg.lyocell.engine.executor;

import com.wilhg.lyocell.engine.ExecutionContext;
import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.engine.WorkloadExecutor;
import com.wilhg.lyocell.engine.scenario.Scenario;
import com.wilhg.lyocell.engine.scenario.SharedIterationsConfig;
import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedIterationsExecutor implements WorkloadExecutor {
    @Override
    public void execute(
        Scenario scenario,
        Path scriptPath,
        Map<String, Object> extraBindings,
        String setupDataJson,
        MetricsCollector metricsCollector,
        TestEngine testEngine
    ) throws InterruptedException, ExecutionException {
        SharedIterationsConfig config = (SharedIterationsConfig) scenario.executor();

        if (config.startTime() != null && !config.startTime().isZero()) {
            Thread.sleep(config.startTime());
        }

        AtomicInteger sharedCounter = new AtomicInteger(0);
        int totalIterations = config.iterations();

        try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
            for (int i = 0; i < config.vus(); i++) {
                int vuId = i;
                scope.fork(() -> {
                    try (JsEngine engine = new JsEngine(extraBindings, metricsCollector, testEngine)) {
                        engine.runScript(scriptPath);
                        Object data = engine.parseJsonData(setupDataJson);

                        while (true) {
                            int iteration = sharedCounter.getAndIncrement();
                            if (iteration >= totalIterations || testEngine.isAborted()) {
                                break;
                            }

                            final int currentIteration = iteration + 1;
                            ScopedValue.where(ExecutionContext.CURRENT, new ExecutionContext(vuId + 1, currentIteration)).run(() -> {
                                long start = System.currentTimeMillis();
                                try {
                                    engine.executeFunction(scenario.exec(), data);
                                    metricsCollector.recordIteration(System.currentTimeMillis() - start, true);
                                } catch (Exception e) {
                                    metricsCollector.recordIteration(System.currentTimeMillis() - start, false);
                                    System.err.println("Iteration failed for VU " + vuId + ": " + e.getMessage());
                                }
                            });
                        }
                    }
                    return null;
                });
            }
            scope.join();
        }
    }
}
