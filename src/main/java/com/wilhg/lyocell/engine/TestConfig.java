package com.wilhg.lyocell.engine;

import java.time.Duration;
import java.util.List;
import java.util.Collections;

public record TestConfig(int vus, int iterations, Duration duration, List<OutputConfig> outputs) {
    public TestConfig(int vus, int iterations, Duration duration) {
        this(vus, iterations, duration, Collections.emptyList());
    }

    public TestConfig(int vus, int iterations) {
        this(vus, iterations, null, Collections.emptyList());
    }
}