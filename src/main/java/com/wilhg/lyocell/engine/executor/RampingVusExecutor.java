package com.wilhg.lyocell.engine.executor;

import com.wilhg.lyocell.engine.ExecutionContext;
import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.engine.WorkloadExecutor;
import com.wilhg.lyocell.engine.scenario.RampingVusConfig;
import com.wilhg.lyocell.engine.scenario.Scenario;
import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RampingVusExecutor implements WorkloadExecutor {
    @Override
    public void execute(
        Scenario scenario,
        Path scriptPath,
        Map<String, Object> extraBindings,
        String setupDataJson,
        MetricsCollector metricsCollector,
        TestEngine testEngine
    ) throws InterruptedException, ExecutionException {
        RampingVusConfig config = (RampingVusConfig) scenario.executor();

        if (config.startTime() != null && !config.startTime().isZero()) {
            Thread.sleep(config.startTime());
        }

        AtomicInteger targetVus = new AtomicInteger(config.startVUs());
        AtomicBoolean running = new AtomicBoolean(true);
        List<Thread> vuThreads = new ArrayList<>();
        AtomicInteger activeVus = new AtomicInteger(0);

        // Controller Thread: Adjusts targetVus based on stages
        Thread controllerThread = Thread.ofVirtual().start(() -> {
            try {
                int currentTarget = config.startVUs();
                for (RampingVusConfig.Stage stage : config.stages()) {
                    long start = System.currentTimeMillis();
                    long durationMs = stage.duration().toMillis();
                    int startVus = currentTarget;
                    int endVus = stage.target();

                    while (System.currentTimeMillis() - start < durationMs && !testEngine.isAborted()) {
                        double progress = (double) (System.currentTimeMillis() - start) / durationMs;
                        currentTarget = (int) (startVus + (endVus - startVus) * progress);
                        targetVus.set(currentTarget);
                        Thread.sleep(100); // Update target every 100ms
                    }
                    currentTarget = endVus;
                    targetVus.set(currentTarget);
                }
            } catch (InterruptedException e) {
                // Stopped
            } finally {
                running.set(false);
            }
        });

        // Monitor loop to spawn/stop VUs to match targetVus
        while (running.get() && !testEngine.isAborted()) {
            int currentTarget = targetVus.get();
            while (activeVus.get() < currentTarget) {
                int vuId = activeVus.getAndIncrement();
                Thread t = Thread.ofVirtual().start(() -> {
                    try (JsEngine engine = new JsEngine(extraBindings, metricsCollector, testEngine)) {
                        engine.runScript(scriptPath);
                        Object data = engine.parseJsonData(setupDataJson);
                        int iteration = 0;
                        while (activeVus.get() <= targetVus.get() && running.get() && !testEngine.isAborted()) {
                            iteration++;
                            ExecutionContext.set(new ExecutionContext(vuId + 1, iteration));
                            long start = System.currentTimeMillis();
                            try {
                                engine.executeFunction(scenario.exec(), data);
                                metricsCollector.recordIteration(System.currentTimeMillis() - start, true);
                            } catch (Exception e) {
                                metricsCollector.recordIteration(System.currentTimeMillis() - start, false);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        activeVus.decrementAndGet();
                        ExecutionContext.remove();
                    }
                });
                vuThreads.add(t);
            }
            Thread.sleep(100);
        }

        controllerThread.join();
        for (Thread t : vuThreads) t.join();
    }
}
