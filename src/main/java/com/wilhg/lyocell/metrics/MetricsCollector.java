package com.wilhg.lyocell.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Double>> trends = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> gauges = new ConcurrentHashMap<>();

    public void addCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(value);
    }

    public void addTrend(String name, double value) {
        List<Double> samples = trends.computeIfAbsent(name, k -> Collections.synchronizedList(new ArrayList<>()));
        samples.add(value);
    }

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
