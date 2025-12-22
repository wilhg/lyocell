package com.wilhg.lyocell.engine;

import java.nio.file.Path;
import java.util.Map;

public class VuWorker implements Runnable {
    private final int id;
    private final Path scriptPath;
    private final Map<String, Object> extraBindings;
    private final String setupDataJson;

    public VuWorker(int id, Path scriptPath, Map<String, Object> extraBindings, String setupDataJson) {
        this.id = id;
        this.scriptPath = scriptPath;
        this.extraBindings = extraBindings;
        this.setupDataJson = setupDataJson;
    }

    @Override
    public void run() {
        try (JsEngine engine = new JsEngine(extraBindings)) {
            // 1. Init: Load the script
            engine.runScript(scriptPath);
            
            // 2. Prepare data
            Object data = engine.parseJsonData(setupDataJson);
            
            // 3. Run default (simple loop for now, just 1 iteration as per current config)
            // TODO: Support loop based on config.iterations/duration
            engine.executeDefault(data);
            
        } catch (Exception e) {
            throw new RuntimeException("VU " + id + " failed", e);
        }
    }
}
