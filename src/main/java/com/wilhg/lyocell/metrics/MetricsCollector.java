package com.wilhg.lyocell.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/// A thread-safe collector for performance metrics using Micrometer.
///
/// This class acts as a facade over Micrometer's MeterRegistry,
/// mapping k6 metric types to Micrometer instruments.
public class MetricsCollector {
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicReference<Double>> gaugeValues = new ConcurrentHashMap<>();

    public MetricsCollector() {
        this(new SimpleMeterRegistry());
    }

    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    /// Adds a value to a cumulative counter.
    ///
    /// @param name The metric name (e.g., "http_reqs")
    /// @param value The value to add
    public void addCounter(String name, long value) {
        registry.counter(name).increment(value);
    }

    /// Adds a sample to a trend metric.
    ///
    /// @param name The metric name (e.g., "http_req_duration")
    /// @param value The sample value
    public void addTrend(String name, double value) {
        DistributionSummary.builder(name)
                .publishPercentiles(0.95, 0.99)
                .register(registry)
                .record(value);
    }

    /// Sets a gauge to a specific value.
    ///
    /// @param name The metric name
    /// @param value The current value
    public void setGauge(String name, double value) {
        gaugeValues.computeIfAbsent(name, k -> {
            AtomicReference<Double> ref = new AtomicReference<>(value);
            Gauge.builder(name, ref, AtomicReference::get).register(registry);
            return ref;
        }).set(value);
    }

    public long getCounterValue(String name) {
        Counter counter = registry.find(name).counter();
        return counter != null ? (long) counter.count() : 0;
    }

    /// Calculates summary statistics for a trend.
    ///
    /// @param name The metric name
    /// @return A summary containing min, max, avg, and percentiles
    public MetricSummary getTrendSummary(String name) {
        DistributionSummary summary = registry.find(name).summary();
        if (summary == null) return new MetricSummary(0, 0, 0, 0, 0, 0);

        return new MetricSummary(
                0, // Micrometer doesn't track absolute min easily without specialized histograms
                summary.max(),
                summary.mean(),
                summary.count(),
                summary.takeSnapshot().percentileValues().length > 0 ? summary.takeSnapshot().percentileValues()[0].value() : 0,
                summary.takeSnapshot().percentileValues().length > 1 ? summary.takeSnapshot().percentileValues()[1].value() : 0
        );
    }
}