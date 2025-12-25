package com.wilhg.lyocell.engine;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

import org.graalvm.polyglot.Value;

import com.wilhg.lyocell.cli.CliAnimation;
import com.wilhg.lyocell.engine.executor.ConstantArrivalRateExecutor;
import com.wilhg.lyocell.engine.executor.ConstantVusExecutor;
import com.wilhg.lyocell.engine.executor.PerVuIterationsExecutor;
import com.wilhg.lyocell.engine.executor.RampingVusExecutor;
import com.wilhg.lyocell.engine.executor.SharedIterationsExecutor;
import com.wilhg.lyocell.engine.scenario.ConstantArrivalRateConfig;
import com.wilhg.lyocell.engine.scenario.ConstantVusConfig;
import com.wilhg.lyocell.engine.scenario.PerVuIterationsConfig;
import com.wilhg.lyocell.engine.scenario.RampingVusConfig;
import com.wilhg.lyocell.engine.scenario.Scenario;
import com.wilhg.lyocell.engine.scenario.SharedIterationsConfig;
import com.wilhg.lyocell.metrics.MetricsCollector;
import com.wilhg.lyocell.metrics.SummaryReporter;
import com.wilhg.lyocell.metrics.TimeSeriesData;
import com.wilhg.lyocell.report.HtmlReportRenderer;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public class TestEngine {
    private final Map<String, Object> extraBindings;
    private final MetricsCollector metricsCollector = new MetricsCollector();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean aborted = false;
    private final List<OutputConfig> initialOutputs;
    private final HtmlReportRenderer htmlReportRenderer = new HtmlReportRenderer();

    public TestEngine(List<OutputConfig> initialOutputs) {
        this(Collections.emptyMap(), initialOutputs);
    }

    public TestEngine(Map<String, Object> extraBindings, List<OutputConfig> initialOutputs) {
        this.extraBindings = extraBindings;
        this.initialOutputs = initialOutputs;
    }

    public void abort() {
        this.aborted = true;
    }

    public boolean isAborted() {
        return aborted;
    }

    private void configureOutputs(List<OutputConfig> outputs) {
        if (outputs == null) return;
        for (OutputConfig output : outputs) {
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

    private final List<String> htmlReportPaths = new java.util.concurrent.CopyOnWriteArrayList<>();

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
                new PerVuIterationsConfig(
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
            case PerVuIterationsConfig _ -> new PerVuIterationsExecutor();
            case SharedIterationsConfig _ -> new SharedIterationsExecutor();
            case ConstantVusConfig _ -> new ConstantVusExecutor();
            case RampingVusConfig _ -> new RampingVusExecutor();
            case ConstantArrivalRateConfig _ -> new ConstantArrivalRateExecutor();
        };
    }

    public void run(Path scriptPath, TestConfig config) throws InterruptedException, ExecutionException {
        // Configure Outputs from initial config
        configureOutputs(initialOutputs);

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
                        options = mapper.readValue(json, new TypeReference<>() {
                        });
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
            try (CliAnimation animation = new CliAnimation("Running test...")) {
                animation.start();
                Set<String> activeScenarios = ConcurrentHashMap.newKeySet();
                try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                    for (Scenario scenario : config.scenarios().values()) {
                        scope.fork(() -> {
                            activeScenarios.add(scenario.name());
                            updateAnimationMessage(animation, activeScenarios);

                            long start = System.currentTimeMillis();
                            try {
                                WorkloadExecutor executor = getExecutor(scenario);
                                executor.execute(scenario, scriptPath, extraBindings, finalSetupDataJson, metricsCollector, this);
                            } finally {
                                long durationMs = System.currentTimeMillis() - start;
                                activeScenarios.remove(scenario.name());
                                updateAnimationMessage(animation, activeScenarios);
                                animation.printLog("Scenario '" + scenario.name() + "' finished in " + formatDuration(durationMs));
                            }
                            return null;
                        });
                    }
                    scope.join();
                }
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
            if (e instanceof InterruptedException) throw e;
            if (e instanceof ExecutionException) throw e;
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
        if (!htmlReportPaths.isEmpty()) {
            SequencedCollection<TimeSeriesData> timelineData = metricsCollector.getIterationTimeline(1000); // 1-second buckets
            for (String pathString : htmlReportPaths) {
                Path targetPath = java.nio.file.Paths.get(pathString);
                if (pathString.isEmpty() || java.nio.file.Files.isDirectory(targetPath)) {
                    String scriptName = scriptPath.getFileName().toString().replace(".js", "");
                    String timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
                    targetPath = targetPath.resolve("lyocell-report-" + scriptName + "-" + timestamp + ".html");
                }
                htmlReportRenderer.generate(metricsCollector, timelineData, targetPath.toString());
            }
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

    void updateAnimationMessage(CliAnimation animation, java.util.Set<String> activeScenarios) {
        if (activeScenarios.isEmpty()) {
            animation.setMessage("Finalizing...");
        } else {
            animation.setMessage("Running: " + String.join(", ", activeScenarios));
        }
    }

    String formatDuration(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes < 60) {
            if (remainingSeconds == 0) {
                return minutes + "m";
            }
            return minutes + "m" + remainingSeconds + "s";
        }
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (remainingMinutes == 0 && remainingSeconds == 0) {
            return hours + "h";
        }
        if (remainingSeconds == 0) {
            return hours + "h" + remainingMinutes + "m";
        }
        return hours + "h" + remainingMinutes + "m" + remainingSeconds + "s";
    }
}
