package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.metrics.MetricsCollector;
import org.graalvm.polyglot.HostAccess;

public class MetricsModule {
    private final MetricsCollector collector;

    public MetricsModule(MetricsCollector collector) {
        this.collector = collector;
    }

    @HostAccess.Export
    public void addCounter(String name, long value) {
        collector.addCounter(name, value);
    }

    @HostAccess.Export
    public void addTrend(String name, double value) {
        collector.addTrend(name, value);
    }

    // We can add Gauge and Rate later using similar logic
}
