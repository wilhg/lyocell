package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.engine.scenario.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScenarioParser {

    public static Map<String, Scenario> parse(Map<String, Object> scenariosMap) {
        Map<String, Scenario> result = new HashMap<>();
        if (scenariosMap == null) return result;

        for (Map.Entry<String, Object> entry : scenariosMap.entrySet()) {
            String name = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) entry.getValue();
            result.put(name, parseScenario(name, configMap));
        }
        return result;
    }

    private static Scenario parseScenario(String name, Map<String, Object> map) {
        String executorType = (String) map.getOrDefault("executor", "per-vu-iterations");
        String exec = (String) map.getOrDefault("exec", "default");

        ExecutorConfig executor = switch (executorType) {
            case "per-vu-iterations" -> parsePerVuIterations(map);
            case "shared-iterations" -> parseSharedIterations(map);
            case "constant-vus" -> parseConstantVus(map);
            case "ramping-vus" -> parseRampingVus(map);
            case "constant-arrival-rate" -> parseConstantArrivalRate(map);
            default -> throw new IllegalArgumentException("Unknown executor type: " + executorType);
        };

        return new Scenario(name, executor, exec);
    }

    private static PerVuIterationsConfig parsePerVuIterations(Map<String, Object> map) {
        return new PerVuIterationsConfig(
            asInt(map.getOrDefault("vus", 1)),
            asInt(map.getOrDefault("iterations", 1)),
            parseDuration(map.get("startTime")),
            parseDuration(map.getOrDefault("gracefulStop", "30s"))
        );
    }

    private static SharedIterationsConfig parseSharedIterations(Map<String, Object> map) {
        return new SharedIterationsConfig(
            asInt(map.getOrDefault("vus", 1)),
            asInt(map.getOrDefault("iterations", 1)),
            parseDuration(map.get("startTime")),
            parseDuration(map.getOrDefault("gracefulStop", "30s")),
            parseDuration(map.get("maxDuration"))
        );
    }

    private static ConstantVusConfig parseConstantVus(Map<String, Object> map) {
        return new ConstantVusConfig(
            asInt(map.getOrDefault("vus", 1)),
            parseDuration(map.get("duration")),
            parseDuration(map.get("startTime")),
            parseDuration(map.getOrDefault("gracefulStop", "30s"))
        );
    }

    private static RampingVusConfig parseRampingVus(Map<String, Object> map) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stagesList = (List<Map<String, Object>>) map.get("stages");
        List<RampingVusConfig.Stage> stages = stagesList == null ? List.of() : stagesList.stream()
            .map(m -> new RampingVusConfig.Stage(parseDuration(m.get("duration")), asInt(m.get("target"))))
            .collect(Collectors.toList());

        return new RampingVusConfig(
            asInt(map.getOrDefault("startVUs", 0)),
            stages,
            parseDuration(map.get("startTime")),
            parseDuration(map.getOrDefault("gracefulStop", "30s")),
            parseDuration(map.getOrDefault("gracefulRampDown", "30s"))
        );
    }

    private static ConstantArrivalRateConfig parseConstantArrivalRate(Map<String, Object> map) {
        return new ConstantArrivalRateConfig(
            asDouble(map.getOrDefault("rate", 0.0)),
            parseDuration(map.getOrDefault("timeUnit", "1s")),
            parseDuration(map.get("duration")),
            asInt(map.getOrDefault("preAllocatedVUs", 0)),
            asInt(map.getOrDefault("maxVUs", 0)),
            parseDuration(map.get("startTime")),
            parseDuration(map.getOrDefault("gracefulStop", "30s"))
        );
    }

    private static int asInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        if (obj instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private static double asDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof String s) return Double.parseDouble(s);
        return 0.0;
    }

    public static Duration parseDuration(Object obj) {
        if (obj == null) return Duration.ZERO;
        if (obj instanceof Duration d) return d;
        String s = obj.toString();
        if (s.isEmpty()) return Duration.ZERO;

        if (s.endsWith("ms")) return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
        if (s.endsWith("s")) return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1)));
        if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
        if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));

        return Duration.ofSeconds(Long.parseLong(s));
    }
}
