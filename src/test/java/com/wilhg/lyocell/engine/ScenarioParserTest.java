package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.engine.scenario.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioParserTest {

    @Test
    void testParsePerVuIterations() {
        Map<String, Object> scenarioMap = Map.of(
            "executor", "per-vu-iterations",
            "vus", 10,
            "iterations", 20,
            "startTime", "10s"
        );
        Map<String, Object> scenariosMap = Map.of("test_scenario", scenarioMap);

        Map<String, Scenario> scenarios = ScenarioParser.parse(scenariosMap);

        assertEquals(1, scenarios.size());
        Scenario scenario = scenarios.get("test_scenario");
        assertEquals("test_scenario", scenario.name());
        assertTrue(scenario.executor() instanceof PerVuIterationsConfig);
        PerVuIterationsConfig config = (PerVuIterationsConfig) scenario.executor();
        assertEquals(10, config.vus());
        assertEquals(20, config.iterations());
        assertEquals(Duration.ofSeconds(10), config.startTime());
    }

    @Test
    void testParseConstantVus() {
        Map<String, Object> scenarioMap = Map.of(
            "executor", "constant-vus",
            "vus", 5,
            "duration", "1m"
        );
        Map<String, Object> scenariosMap = Map.of("const_vus", scenarioMap);

        Map<String, Scenario> scenarios = ScenarioParser.parse(scenariosMap);

        assertEquals(1, scenarios.size());
        Scenario scenario = scenarios.get("const_vus");
        assertTrue(scenario.executor() instanceof ConstantVusConfig);
        ConstantVusConfig config = (ConstantVusConfig) scenario.executor();
        assertEquals(5, config.vus());
        assertEquals(Duration.ofMinutes(1), config.duration());
    }

    @Test
    void testParseMultipleScenarios() {
        Map<String, Object> scenariosMap = Map.of(
            "s1", Map.of("executor", "per-vu-iterations", "vus", 1),
            "s2", Map.of("executor", "constant-vus", "vus", 2, "duration", "1s")
        );

        Map<String, Scenario> scenarios = ScenarioParser.parse(scenariosMap);

        assertEquals(2, scenarios.size());
        assertTrue(scenarios.containsKey("s1"));
        assertTrue(scenarios.containsKey("s2"));
    }
}
