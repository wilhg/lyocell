package com.wilhg.lyocell.engine.scenario;

import java.time.Duration;

public record ConstantArrivalRateConfig(
    double rate,
    Duration timeUnit,
    Duration duration,
    int preAllocatedVUs,
    int maxVUs,
    Duration startTime,
    Duration gracefulStop
) implements ExecutorConfig {
    @Override
    public String type() {
        return "constant-arrival-rate";
    }
}
