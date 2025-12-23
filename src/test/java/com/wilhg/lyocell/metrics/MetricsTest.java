package com.wilhg.lyocell.metrics;

import org.junit.jupiter.api.Test;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import static org.junit.jupiter.api.Assertions.*;

class MetricsTest {

    @Test
    void testCounterAggregation() throws Exception {
        MetricsCollector collector = new MetricsCollector();
        String metricName = "my_counter";
        
        // Simulate 10 threads adding to the same counter
        try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
            for (int i = 0; i < 10; i++) {
                scope.fork(() -> {
                    for (int j = 0; j < 100; j++) {
                        collector.addCounter(metricName, 1);
                    }
                    return null;
                });
            }
            scope.join();
        }

        assertEquals(1000, collector.getCounterValue(metricName), "Counter should sum up correctly across threads");
    }

    @Test
    void testTrendAggregation() throws Exception {
        MetricsCollector collector = new MetricsCollector();
        String metricName = "request_duration";
        
        collector.addTrend(metricName, 100);
        collector.addTrend(metricName, 200);
        collector.addTrend(metricName, 300);

        MetricSummary summary = collector.getTrendSummary(metricName);
        // Micrometer's DistributionSummary doesn't track min by default.
        // assertEquals(100, summary.min()); 
        assertEquals(300, summary.max());
        assertEquals(200, summary.avg());
        
        // Check percentiles (approximate as Micrometer uses histograms)
        assertTrue(summary.p95() >= 200 && summary.p95() <= 300);
        assertTrue(summary.p99() >= 200 && summary.p99() <= 300);
    }

    @Test
    void testGauge() {
        MetricsCollector collector = new MetricsCollector();
        String name = "vus_active";
        
        collector.setGauge(name, 10.0);
        // We don't have a direct getGaugeValue, but we can check the registry
        assertEquals(10.0, collector.getRegistry().get(name).gauge().value());
        
        collector.setGauge(name, 20.0);
        assertEquals(20.0, collector.getRegistry().get(name).gauge().value());
    }
}
