package com.wilhg.lyocell.engine.scenario;

import java.time.Duration;
import java.util.List;

public record RampingVusConfig(
    int startVUs,
    List<Stage> stages,
    Duration startTime,
    Duration gracefulStop,
    Duration gracefulRampDown
) implements ExecutorConfig {
    public record Stage(Duration duration, int target) {}

    @Override
    public String type() {
        return "ramping-vus";
    }
}
