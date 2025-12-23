package com.wilhg.lyocell.engine.scenario;

import java.time.Duration;

public record ConstantVusConfig(
    int vus,
    Duration duration,
    Duration startTime,
    Duration gracefulStop
) implements ExecutorConfig {
    @Override
    public String type() {
        return "constant-vus";
    }
}
