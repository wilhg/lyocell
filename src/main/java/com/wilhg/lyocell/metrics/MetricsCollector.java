package com.wilhg.lyocell.metrics;

import java.util.DoubleSummaryStatistics;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DoubleSummaryStatistics> trends = new ConcurrentHashMap<>();

    public void addCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(value);
    }

    public void addTrend(String name, double value) {
        DoubleSummaryStatistics stats = trends.computeIfAbsent(name, k -> new DoubleSummaryStatistics());
        synchronized (stats) {
            stats.accept(value);
        }
    }

    public long getCounterValue(String name) {
        LongAdder adder = counters.get(name);
        return adder != null ? adder.sum() : 0;
    }

    public MetricSummary getTrendSummary(String name) {
        DoubleSummaryStatistics stats = trends.get(name);
        if (stats == null) return new MetricSummary(0, 0, 0, 0);
        
        synchronized (stats) {
            return new MetricSummary(
                stats.getMin(),
                stats.getMax(),
                stats.getAverage(),
                stats.getCount()
            );
        }
    }
}
