package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsMetricsIntegrationTest {
    @Test
    public void testCustomMetrics() throws Exception {
        String script = """
            import { Counter, Trend, Gauge, Rate } from 'lyocell/metrics';
            
            const myCounter = new Counter('my_counter');
            const myTrend = new Trend('my_trend');
            const myGauge = new Gauge('my_gauge');
            const myRate = new Rate('my_rate');
            
            export default function() {
                myCounter.add(1);
                myTrend.add(100);
                myGauge.add(42);
                myRate.add(true);
                myRate.add(false);
            }
            """;
        Path scriptPath = Files.createTempFile("test-metrics", ".js");
        Files.writeString(scriptPath, script);
        
        MetricsCollector collector = new MetricsCollector();
        try (JsEngine engine = new JsEngine(Map.of(), collector)) {
            engine.runScript(scriptPath);
            engine.executeDefault(null);
        }
        
        assertEquals(1, collector.getCounterValue("my_counter"));
        assertEquals(1, collector.getCounterValue("my_rate.true"));
        assertEquals(2, collector.getCounterValue("my_rate.total"));
        // Gauge and Trend are already tested in MetricsTest.java, 
        // but here we verify the JS binding works.
    }
}
