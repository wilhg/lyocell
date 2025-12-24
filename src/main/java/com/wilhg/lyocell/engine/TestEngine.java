package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.engine.scenario.Scenario;
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
    private volatile boolean aborted = false;

    public TestEngine() {
        this(Collections.emptyMap());
    }

    public void abort() {
        this.aborted = true;
    }

    public boolean isAborted() {
        return aborted;
    }

    public TestEngine(Map<String, Object> extraBindings) {
        this.extraBindings = extraBindings;
    }

    private void configureOutputs(TestConfig config) {
        if (config.outputs() == null) return;

        for (OutputConfig output : config.outputs()) {
            registerOutput(output);
        }
    }

    @SuppressWarnings("unchecked")
    private void configureOutputsFromOptions(Map<String, Object> options) {
        if (options == null || !options.containsKey("lyocell")) return;
        Map<String, Object> lyocell = (Map<String, Object>) options.get("lyocell");
        if (lyocell == null || !lyocell.containsKey("outputs")) return;

        List<Map<String, Object>> outputs = (List<Map<String, Object>>) lyocell.get("outputs");
        for (Map<String, Object> outputMap : outputs) {
            String type = (String) outputMap.get("type");
            String target = (String) outputMap.get("url"); // k6 uses 'url'
            if (target == null) target = (String) outputMap.get("target");
            if (type != null) {
                registerOutput(new OutputConfig(type, target != null ? target : ""));
            }
        }
    }

    private final java.util.List<String> htmlReportPaths = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private void registerOutput(OutputConfig output) {
        if ("html".equals(output.type())) {
            htmlReportPaths.add(output.target());
        }
    }

    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }

    private TestConfig updateConfigWithScenarios(TestConfig config, Map<String, Scenario> scenarios) {
        return new TestConfig(
            config.vus(),
            config.iterations(),
            config.duration(),
            config.outputs(),
            scenarios
        );
    }

    private TestConfig createDefaultScenario(TestConfig config) {
        Scenario defaultScenario = new Scenario(
            "default",
            new com.wilhg.lyocell.engine.scenario.PerVuIterationsConfig(
                config.vus(),
                config.iterations(),
                java.time.Duration.ZERO,
                java.time.Duration.ofSeconds(30)
            )
        );
        return updateConfigWithScenarios(config, Map.of("default", defaultScenario));
    }

    private WorkloadExecutor getExecutor(Scenario scenario) {
        return switch (scenario.executor()) {
            case com.wilhg.lyocell.engine.scenario.PerVuIterationsConfig _ -> new com.wilhg.lyocell.engine.executor.PerVuIterationsExecutor();
            case com.wilhg.lyocell.engine.scenario.SharedIterationsConfig _ -> new com.wilhg.lyocell.engine.executor.SharedIterationsExecutor();
            case com.wilhg.lyocell.engine.scenario.ConstantVusConfig _ -> new com.wilhg.lyocell.engine.executor.ConstantVusExecutor();
            case com.wilhg.lyocell.engine.scenario.RampingVusConfig _ -> new com.wilhg.lyocell.engine.executor.RampingVusExecutor();
            case com.wilhg.lyocell.engine.scenario.ConstantArrivalRateConfig _ -> new com.wilhg.lyocell.engine.executor.ConstantArrivalRateExecutor();
        };
    }

    public void run(Path scriptPath, TestConfig config) throws InterruptedException, ExecutionException {
        // Configure Outputs
        configureOutputs(config);

        String setupDataJson = null;
        Map<String, Object> options = null;

        // 1. Setup Phase (Single Thread)
        try (JsEngine setupEngine = new JsEngine(extraBindings, metricsCollector, this)) {
            try {
                setupEngine.runScript(scriptPath);
                Value optionsValue = setupEngine.getOptions();
                if (optionsValue != null) {
                    String json = setupEngine.toJson(optionsValue);
                    if (json != null) {
                        options = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                        configureOutputsFromOptions(options);
                        
                        if (options.containsKey("scenarios")) {
                             @SuppressWarnings("unchecked")
                             Map<String, Object> scenariosMap = (Map<String, Object>) options.get("scenarios");
                             Map<String, Scenario> scenarios = ScenarioParser.parse(scenariosMap);
                             config = updateConfigWithScenarios(config, scenarios);
                        } else if (options.containsKey("stages")) {
                            Map<String, Object> rampingConfig = new java.util.HashMap<>(options);
                            rampingConfig.put("executor", "ramping-vus");
                            Scenario scenario = ScenarioParser.parse(Map.of("default", rampingConfig)).get("default");
                            config = updateConfigWithScenarios(config, Map.of("default", scenario));
                        }
                    }
                }
                
                // If no scenarios in options, and none in config, create default
                if (config.scenarios().isEmpty()) {
                    config = createDefaultScenario(config);
                }

                if (setupEngine.hasExport("setup")) {
                    var data = setupEngine.executeSetup();
                    setupDataJson = setupEngine.toJson(data);
                }
            } catch (Exception e) {
                throw new RuntimeException("Setup failed", e);
            }
        
            final String finalSetupDataJson = setupDataJson;

            // 2. Execution Phase (Parallel Scenarios)
            try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                for (Scenario scenario : config.scenarios().values()) {
                    scope.fork(() -> {
                        WorkloadExecutor executor = getExecutor(scenario);
                        executor.execute(scenario, scriptPath, extraBindings, finalSetupDataJson, metricsCollector, this);
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
        } finally {
            // Close registries to flush metrics
            metricsCollector.getRegistry().close();
        }

        // 4. Check Thresholds
        checkThresholds(options);

        // 5. Final Report
        new SummaryReporter().report(metricsCollector);
        
        // 6. Generate HTML Reports
        com.wilhg.lyocell.report.HtmlReportRenderer htmlRenderer = new com.wilhg.lyocell.report.HtmlReportRenderer();
        for (String pathString : htmlReportPaths) {
            Path targetPath = java.nio.file.Paths.get(pathString);
            if (java.nio.file.Files.isDirectory(targetPath)) {
                String scriptName = scriptPath.getFileName().toString().replace(".js", "");
                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
                targetPath = targetPath.resolve("lyocell-report-" + scriptName + "-" + timestamp + ".html");
            }
            htmlRenderer.generate(metricsCollector, targetPath.toString());
        }
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
