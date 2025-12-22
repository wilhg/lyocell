package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.metrics.MetricsCollector;
import java.nio.file.Path;
import java.util.Map;

public class VuWorker implements Runnable {
    private final int id;
    private final Path scriptPath;
    private final Map<String, Object> extraBindings;
    private final String setupDataJson;
    private final MetricsCollector metricsCollector;

    public VuWorker(int id, Path scriptPath, Map<String, Object> extraBindings, String setupDataJson, MetricsCollector metricsCollector) {
        this.id = id;
        this.scriptPath = scriptPath;
        this.extraBindings = extraBindings;
        this.setupDataJson = setupDataJson;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public void run() {
        try (JsEngine engine = new JsEngine(extraBindings, metricsCollector)) {
            // 1. Init: Load the script
            engine.runScript(scriptPath);
            
            // 2. Prepare data
            Object data = engine.parseJsonData(setupDataJson);
            
            // 3. Run default
            engine.executeDefault(data);
            metricsCollector.addCounter("iterations", 1);
            
        } catch (Exception e) {
            throw new RuntimeException("VU " + id + " failed", e);
        }
    }
}
