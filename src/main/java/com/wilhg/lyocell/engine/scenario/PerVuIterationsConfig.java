package com.wilhg.lyocell.engine.scenario;

import java.time.Duration;

public record PerVuIterationsConfig(
    int vus,
    int iterations,
    Duration startTime,
    Duration gracefulStop
) implements ExecutorConfig {
    @Override
    public String type() {
        return "per-vu-iterations";
    }
}
