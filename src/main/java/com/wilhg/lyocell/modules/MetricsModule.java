package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.metrics.MetricsCollector;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

public class MetricsModule implements LyocellModule {
    private MetricsCollector collector;

    public MetricsModule() {
    }

    public MetricsModule(MetricsCollector collector) {
        this.collector = collector;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.collector = moduleContext.metricsCollector();
        context.getBindings("js").putMember("LyocellMetrics", this);
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
