package com.wilhg.lyocell.engine;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.wilhg.lyocell.metrics.MetricsCollector;
import com.wilhg.lyocell.metrics.SummaryReporter;
import org.graalvm.polyglot.Value;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

public class TestEngine {
    private final Map<String, Object> extraBindings;
    private final MetricsCollector metricsCollector = new MetricsCollector();
    private final ObjectMapper mapper = new ObjectMapper();

    public TestEngine() {
        this(Collections.emptyMap());
    }

    public TestEngine(Map<String, Object> extraBindings) {
        this.extraBindings = extraBindings;
    }

    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    public void run(Path scriptPath, TestConfig config) throws InterruptedException, ExecutionException {
        String setupDataJson = null;
        Map<String, Object> options = null;

        // 1. Setup Phase (Single Thread)
        try (JsEngine setupEngine = new JsEngine(extraBindings, metricsCollector)) {
            try {
                setupEngine.runScript(scriptPath);
                Value optionsValue = setupEngine.getOptions();
                if (optionsValue != null) {
                    String json = setupEngine.toJson(optionsValue);
                    if (json != null) {
                        options = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                    }
                }
                
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
                        new VuWorker(vuId, scriptPath, extraBindings, finalSetupDataJson, metricsCollector).run();
                        return null;
                    });
                }
                scope.join();
            }

            // 3. Teardown Phase
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

        // 4. Check Thresholds
        checkThresholds(options);

        // 5. Final Report
        new SummaryReporter().report(metricsCollector);
    }

    @SuppressWarnings("unchecked")
    private void checkThresholds(Map<String, Object> options) {
        if (options == null || !options.containsKey("thresholds")) return;
        
        Map<String, Object> thresholds = (Map<String, Object>) options.get("thresholds");
        
        for (Map.Entry<String, Object> entry : thresholds.entrySet()) {
            String metricName = entry.getKey();
            List<String> rules = (List<String>) entry.getValue();
            
            for (String rule : rules) {
                checkRule(metricName, rule);
            }
        }
    }

    private void checkRule(String metricName, String rule) {
        if (metricName.equals("checks") && rule.startsWith("rate<")) {
            double limit = Double.parseDouble(rule.substring(5));
            long pass = metricsCollector.getCounterValue("checks.pass");
            long fail = metricsCollector.getCounterValue("checks.fail");
            long total = pass + fail;
            
            if (total > 0) {
                double failureRate = (double) fail / total;
                if (failureRate > limit) {
                    throw new RuntimeException("Thresholds failed: checks failure rate " + failureRate + " > " + limit);
                }
            }
        }
    }
}
