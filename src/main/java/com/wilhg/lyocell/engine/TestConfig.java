package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.engine.scenario.Scenario;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public record TestConfig(
    int vus, 
    int iterations, 
    Duration duration, 
    List<OutputConfig> outputs,
    Map<String, Scenario> scenarios
) {
    public TestConfig(int vus, int iterations, Duration duration, List<OutputConfig> outputs) {
        this(vus, iterations, duration, outputs, Collections.emptyMap());
    }

    public TestConfig(int vus, int iterations, Duration duration) {
        this(vus, iterations, duration, Collections.emptyList(), Collections.emptyMap());
    }

    public TestConfig(int vus, int iterations) {
        this(vus, iterations, null, Collections.emptyList(), Collections.emptyMap());
    }
}