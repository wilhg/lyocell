package com.wilhg.lyocell.engine.scenario;

import java.time.Duration;

public record SharedIterationsConfig(
    int vus,
    int iterations,
    Duration startTime,
    Duration gracefulStop,
    Duration maxDuration
) implements ExecutorConfig {
    @Override
    public String type() {
        return "shared-iterations";
    }
}
