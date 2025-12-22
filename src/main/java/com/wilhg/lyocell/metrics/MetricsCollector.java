package com.wilhg.lyocell.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/// A thread-safe collector for performance metrics.
///
/// This class aggregates counters, gauges, and trends from multiple
/// concurrent Virtual Users.
///
/// ## Key Features
/// * **Lock-free Counters**: Uses `LongAdder` for high-throughput counting.
/// * **Trend Percentiles**: Calculates p95, p99, etc.
/// * **Thread Safety**: Safe for use by thousands of Virtual Threads.
public class MetricsCollector {
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Double>> trends = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> gauges = new ConcurrentHashMap<>();

    /// Adds a value to a cumulative counter.
    ///
    /// @param name The metric name (e.g., "http_reqs")
    /// @param value The value to add
    public void addCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(value);
    }

    /// Adds a sample to a trend metric.
    ///
    /// @param name The metric name (e.g., "http_req_duration")
    /// @param value The sample value
    public void addTrend(String name, double value) {
        List<Double> samples = trends.computeIfAbsent(name, k -> Collections.synchronizedList(new ArrayList<>()));
        samples.add(value);
    }

    /// Sets a gauge to a specific value.
    ///
    /// @param name The metric name
    /// @param value The current value
    public void setGauge(String name, double value) {
        gauges.put(name, value);
    }

    public long getCounterValue(String name) {
        LongAdder adder = counters.get(name);
        return adder != null ? adder.sum() : 0;
    }

    public ConcurrentHashMap<String, LongAdder> getCounters() {
        return counters;
    }

    public ConcurrentHashMap<String, List<Double>> getTrends() {
        return trends;
    }

    /// Calculates summary statistics for a trend.
    ///
    /// @param name The metric name
    /// @return A summary containing min, max, avg, and percentiles
    public MetricSummary getTrendSummary(String name) {
        List<Double> samples = trends.get(name);
        if (samples == null || samples.isEmpty()) return new MetricSummary(0, 0, 0, 0, 0, 0);
        
        synchronized (samples) {
            List<Double> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);
            
            double sum = 0;
            double min = sorted.getFirst();
            double max = sorted.getLast();
            for (double d : sorted) sum += d;
            
            double avg = sum / sorted.size();
            double p95 = sorted.get((int) (sorted.size() * 0.95));
            double p99 = sorted.get((int) (sorted.size() * 0.99));
            
            return new MetricSummary(min, max, avg, sorted.size(), p95, p99);
        }
    }
}
