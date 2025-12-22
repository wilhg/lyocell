package com.wilhg.lyocell.engine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

public class TestEngine {
    private final Map<String, Object> extraBindings;

    public TestEngine() {
        this(Collections.emptyMap());
    }

    public TestEngine(Map<String, Object> extraBindings) {
        this.extraBindings = extraBindings;
    }

    public void run(Path scriptPath, TestConfig config) throws InterruptedException, ExecutionException {
        String setupDataJson = null;

        // 1. Setup Phase (Single Thread)
        // We use a separate engine for setup/teardown
        try (JsEngine setupEngine = new JsEngine(extraBindings)) {
            try {
                setupEngine.runScript(scriptPath);
                
                // Run setup() if it exists
                if (setupEngine.hasExport("setup")) {
                    var data = setupEngine.executeSetup();
                    setupDataJson = setupEngine.toJson(data);
                }
            } catch (Exception e) {
                throw new RuntimeException("Setup failed", e);
            }
        
            // 2. Execution Phase (Concurrent VUs)
            try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                for (int i = 0; i < config.vus(); i++) {
                    int vuId = i;
                    String finalSetupDataJson = setupDataJson;
                    scope.fork(() -> {
                        new VuWorker(vuId, scriptPath, extraBindings, finalSetupDataJson).run();
                        return null;
                    });
                }
                scope.join();
            }

            // 3. Teardown Phase (Single Thread)
            // We reuse the setup engine instance to mimic k6 behavior where teardown runs in a similar context context
            // actually k6 runs teardown in a fresh VU usually, but reusing this one is efficient for now.
            try {
                if (setupEngine.hasExport("teardown")) {
                     Object data = setupEngine.parseJsonData(setupDataJson);
                     setupEngine.executeTeardown(data);
                }
            } catch (Exception e) {
                 throw new RuntimeException("Teardown failed", e);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ExecutionException) throw (ExecutionException) e;
            throw new RuntimeException("Test execution failed", e);
        }
    }
}
