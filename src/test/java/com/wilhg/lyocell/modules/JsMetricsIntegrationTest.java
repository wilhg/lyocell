import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JsMetricsIntegrationTest {
    @Test
    void shouldExecuteScriptWithJsEngineAndCollector() {
        MetricsCollector collector = new MetricsCollector();
        // Initialize TestEngine with empty OutputConfig list
        TestEngine testEngine = new TestEngine(Collections.emptyList());
        assertDoesNotThrow(() -> {
            // Pass MetricsCollector and TestEngine instance to JsEngine constructor
            try (JsEngine engine = new JsEngine(collector, testEngine)) {
                engine.eval("var k6 = {}; k6.metrics = {}; k6.sleep = function() {};");
                engine.eval("var count = 0;");
                engine.eval("for (let i = 0; i < 1000; i++) { count++; }");
                engine.eval("k6.metrics.myCounter = { add: function(val) { globalThis.LyocellMetrics.addCounter('myCounter', val); } };");
                engine.eval("k6.metrics.myCounter.add(1);");
            }
        });
        assert collector.getCounterValue("myCounter") == 1;
    }
}
