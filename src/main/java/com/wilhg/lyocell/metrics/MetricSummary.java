package com.wilhg.lyocell.metrics;

public record MetricSummary(double min, double max, double avg, long count, double p95, double p99) {
}
