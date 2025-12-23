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
    public String getName() {
        return "k6/metrics";
    }

    @Override
    public String getJsSource() {
        return """
            const Metrics = globalThis.LyocellMetrics;
            export class Counter {
                constructor(name) { this.name = name; }
                add(val) { Metrics.addCounter(this.name, val); }
            }
            export class Trend {
                constructor(name) { this.name = name; }
                add(val) { Metrics.addTrend(this.name, val); }
            }
            export class Gauge {
                constructor(name) { this.name = name; }
                add(val) { Metrics.setGauge(this.name, val); }
            }
            export class Rate {
                constructor(name) { this.name = name; }
                add(val) { Metrics.addRate(this.name, val); }
            }
            export default { Counter, Trend, Gauge, Rate };
            """;
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

    @HostAccess.Export
    public void setGauge(String name, double value) {
        collector.setGauge(name, value);
    }

    @HostAccess.Export
    public void addRate(String name, boolean value) {
        collector.addRate(name, value);
    }
}
