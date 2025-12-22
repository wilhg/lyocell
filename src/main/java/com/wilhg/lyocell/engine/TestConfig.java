package com.wilhg.lyocell.engine;

import java.time.Duration;

public record TestConfig(int vus, int iterations, Duration duration) {
}
