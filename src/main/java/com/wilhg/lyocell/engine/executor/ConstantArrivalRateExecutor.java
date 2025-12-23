package com.wilhg.lyocell.engine.executor;

import com.wilhg.lyocell.engine.ExecutionContext;
import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.engine.WorkloadExecutor;
import com.wilhg.lyocell.engine.scenario.ConstantArrivalRateConfig;
import com.wilhg.lyocell.engine.scenario.Scenario;
import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConstantArrivalRateExecutor implements WorkloadExecutor {
    @Override
    public void execute(
        Scenario scenario,
        Path scriptPath,
        Map<String, Object> extraBindings,
        String setupDataJson,
        MetricsCollector metricsCollector,
        TestEngine testEngine
    ) throws InterruptedException, ExecutionException {
        ConstantArrivalRateConfig config = (ConstantArrivalRateConfig) scenario.executor();

        if (config.startTime() != null && !config.startTime().isZero()) {
            Thread.sleep(config.startTime());
        }

        // Use a pool for VUs. k6 calls these preAllocatedVUs.
        ExecutorService vuPool = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger activeIterations = new AtomicInteger(0);
        AtomicInteger iterationCounter = new AtomicInteger(0);

        long durationMs = config.duration().toMillis();
        long startTime = System.currentTimeMillis();
        double ratePerMs = config.rate() / config.timeUnit().toMillis();
        
        long iterationsTriggered = 0;

        while (System.currentTimeMillis() - startTime < durationMs && !testEngine.isAborted()) {
            long elapsed = System.currentTimeMillis() - startTime;
            long shouldHaveTriggered = (long) (elapsed * ratePerMs);

            while (iterationsTriggered < shouldHaveTriggered && !testEngine.isAborted()) {
                iterationsTriggered++;
                int iterationId = iterationCounter.incrementAndGet();
                
                vuPool.submit(() -> {
                    activeIterations.incrementAndGet();
                    try (JsEngine engine = new JsEngine(extraBindings, metricsCollector, testEngine)) {
                        engine.runScript(scriptPath);
                        Object data = engine.parseJsonData(setupDataJson);
                        
                        ExecutionContext.set(new ExecutionContext(0, iterationId));
                        long start = System.currentTimeMillis();
                        try {
                            engine.executeFunction(scenario.exec(), data);
                            metricsCollector.recordIteration(System.currentTimeMillis() - start, true);
                        } catch (Exception e) {
                            metricsCollector.recordIteration(System.currentTimeMillis() - start, false);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        activeIterations.decrementAndGet();
                        ExecutionContext.remove();
                    }
                });
            }
            
            Thread.sleep(1); // Small sleep to prevent busy wait
        }

        vuPool.shutdown();
        vuPool.awaitTermination(config.gracefulStop().toMillis(), TimeUnit.MILLISECONDS);
    }
}
