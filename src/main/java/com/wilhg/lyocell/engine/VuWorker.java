package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.Map;

public class VuWorker implements Runnable {
    private final int vuId;
    private final Path scriptPath;
    private final Map<String, Object> extraBindings;
    private final String setupDataJson;
    private final MetricsCollector metricsCollector;
    private final int iterations;
    private final TestEngine testEngine;
    private final String exec;

    public VuWorker(
        int vuId, 
        Path scriptPath, 
        Map<String, Object> extraBindings, 
        String setupDataJson, 
        MetricsCollector metricsCollector, 
        int iterations,
        TestEngine testEngine
    ) {
        this(vuId, scriptPath, extraBindings, setupDataJson, metricsCollector, iterations, testEngine, "default");
    }

    public VuWorker(
        int vuId, 
        Path scriptPath, 
        Map<String, Object> extraBindings, 
        String setupDataJson, 
        MetricsCollector metricsCollector, 
        int iterations,
        TestEngine testEngine,
        String exec
    ) {
        this.vuId = vuId;
        this.scriptPath = scriptPath;
        this.extraBindings = extraBindings;
        this.setupDataJson = setupDataJson;
        this.metricsCollector = metricsCollector;
        this.iterations = iterations;
        this.testEngine = testEngine;
        this.exec = exec;
    }

    @Override
    public void run() {
        try (JsEngine engine = new JsEngine(extraBindings, metricsCollector, testEngine)) {
            engine.runScript(scriptPath);
            Object data = engine.parseJsonData(setupDataJson);

            ExecutionContext.set(new ExecutionContext(vuId + 1, 0));

            for (int i = 0; i < iterations; i++) {
                if (testEngine.isAborted()) break;
                
                ExecutionContext.set(new ExecutionContext(vuId + 1, i + 1));
                
                long start = System.currentTimeMillis();
                try {
                    executeFunction(engine, exec, data);
                    metricsCollector.recordIteration(System.currentTimeMillis() - start, true);
                } catch (Exception e) {
                    metricsCollector.recordIteration(System.currentTimeMillis() - start, false);
                    System.err.println("Iteration failed for VU " + vuId + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("VU " + vuId + " failed to initialize: " + e.getMessage());
        } finally {
            ExecutionContext.remove();
        }
    }

    private void executeFunction(JsEngine engine, String funcName, Object data) {
        if (funcName.equals("default")) {
            engine.executeDefault(data);
        } else {
            // Execute custom function
            if (engine.hasExport(funcName)) {
                // We might need to add a generic execute method to JsEngine
                engine.executeFunction(funcName, data);
            } else {
                throw new RuntimeException("Function not found: " + funcName);
            }
        }
    }


}
