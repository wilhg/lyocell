package com.wilhg.lyocell.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.wilhg.lyocell.engine.ExecutionContext;
import com.wilhg.lyocell.metrics.MetricSummary;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/// A thread-safe collector for performance metrics using Micrometer.
///
/// This class acts as a facade over Micrometer's MeterRegistry,
/// mapping k6 metric types to Micrometer instruments.
public class MetricsCollector {
    private final CompositeMeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicReference<Double>> gaugeValues = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<TimelineEvent> timelineEvents = new ConcurrentLinkedQueue<>();

    // Internal record to capture timeline events with timestamp
    private record TimelineEvent(long timestamp, boolean success) {
    }

    public MetricsCollector() {
        this.registry = new CompositeMeterRegistry();
        this.registry.add(new SimpleMeterRegistry());
    }

    public CompositeMeterRegistry getRegistry() {
        return registry;
    }

    public void addRegistry(MeterRegistry childRegistry) {
        this.registry.add(childRegistry);
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

    /// Adds a boolean sample to a rate metric.
    ///
    /// @param name The metric name
    /// @param value The sample value (true/false)
    public void addRate(String name, boolean value) {
        registry.counter(name + ".total").increment();
        if (value) {
            registry.counter(name + ".true").increment();
        }
    }

    /**
     * Records an iteration event along with its success status and timestamp.
     * Also updates cumulative iteration counters.
     *
     * @param duration The duration of the iteration in milliseconds.
     * @param success True if the iteration was successful, false otherwise.
     */
    public void recordIteration(long duration, boolean success) {
        addTrend("iteration_duration", duration);
        addCounter("iterations", 1);
        
        boolean finalSuccess = success;
        ExecutionContext ctx = ExecutionContext.get();
        if (ctx != null && ctx.isFailed()) {
            finalSuccess = false;
        }

        if (!finalSuccess) {
            addCounter("iterations_failed", 1);
        }
        recordTimelineEvent(finalSuccess);
    }

    /**
     * Records a generic event for the timeline (e.g., a check or an HTTP request).
     *
     * @param success True if the event was successful, false otherwise.
     */
    public void recordTimelineEvent(boolean success) {
        timelineEvents.offer(new TimelineEvent(System.currentTimeMillis(), success));
    }

    /**
     * Aggregates raw timeline events into time-series data based on specified bucket duration.
     *
     * @param bucketDurationMillis The duration of each time bucket in milliseconds.
     * @return A list of TimeSeriesData, sorted by timestamp.
     */
    public java.util.SequencedCollection<TimeSeriesData> getIterationTimeline(long bucketDurationMillis) {
        if (timelineEvents.isEmpty()) {
            return java.util.List.of();
        }

        long startTime = timelineEvents.stream()
                .mapToLong(TimelineEvent::timestamp)
                .min()
                .orElse(System.currentTimeMillis());

        Map<Long, List<TimelineEvent>> groupedByBucket = timelineEvents.stream()
                .collect(Collectors.groupingBy(event -> {
                    long relativeTime = event.timestamp() - startTime;
                    return (relativeTime / bucketDurationMillis) * bucketDurationMillis;
                }));

        return groupedByBucket.entrySet().stream()
                .map(entry -> {
                    long bucketStartRelativeTime = entry.getKey();
                    long successful = 0;
                    long failed = 0;
                    for (TimelineEvent event : entry.getValue()) {
                        if (event.success()) {
                            successful++;
                        } else {
                            failed++;
                        }
                    }
                    return new TimeSeriesData(startTime + bucketStartRelativeTime, successful, failed);
                })
                .sorted(Comparator.comparing(TimeSeriesData::timestamp))
                .toList();
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