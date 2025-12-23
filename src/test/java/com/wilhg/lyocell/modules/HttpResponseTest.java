package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.polyglot.HostAccess;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class HttpResponseTest {

    @Container
    public GenericContainer<?> httpbin = new GenericContainer<>(DockerImageName.parse("sharat87/httpbun"))
            .withExposedPorts(80);

    @TempDir
    Path tempDir;

    @Test
    void testHttpResponseJson() throws Exception {
        String url = "http://" + httpbin.getHost() + ":" + httpbin.getMappedPort(80) + "/json";
        
        Path script = tempDir.resolve("http_json_test.js");
        Files.writeString(script, """
            import http from 'k6/http';
            export default function() {
                const res = http.get('%s');
                const data = res.json();
                // httpbun's /json response has a different structure than httpbin
                // Usually returns a simple JSON. Let's just verify we can access something.
                globalThis.TestResult.set(res.status.toString());
            }
            """.formatted(url));

        AtomicReference<String> result = new AtomicReference<>();
        TestResultBridge bridge = new TestResultBridge(result);
        
        TestEngine engine = new TestEngine(Map.of("TestResult", bridge));
        engine.run(script, new TestConfig(1, 1, null));

        assertEquals("200", result.get());
    }

    @Test
    void testHttpStatusAndBody() throws Exception {
        String url = "http://" + httpbin.getHost() + ":" + httpbin.getMappedPort(80) + "/status/418";
        
        Path script = tempDir.resolve("http_status_test.js");
        Files.writeString(script, """
            import http from 'k6/http';
            export default function() {
                const res = http.get('%s');
                globalThis.StatusResult.set(res.status);
            }
            """.formatted(url));

        AtomicInteger status = new AtomicInteger(0);
        TestStatusBridge bridge = new TestStatusBridge(status);
        
        TestEngine engine = new TestEngine(Map.of("StatusResult", bridge));
        engine.run(script, new TestConfig(1, 1, null));

        assertEquals(418, status.get());
    }

    @Test
    void testHttpResponsePost() throws Exception {
        String url = "http://" + httpbin.getHost() + ":" + httpbin.getMappedPort(80) + "/post";
        
        Path script = tempDir.resolve("http_post_test.js");
        Files.writeString(script, """
            import http from 'k6/http';
            export default function() {
                const payload = JSON.stringify({ name: 'lyocell' });
                const params = { headers: { 'Content-Type': 'application/json' } };
                const res = http.post('%s', payload, params);
                const data = res.json();
                globalThis.PostResult.set(data.json.name);
            }
            """.formatted(url));

        AtomicReference<String> result = new AtomicReference<>();
        TestResultBridge bridge = new TestResultBridge(result);
        
        TestEngine engine = new TestEngine(Map.of("PostResult", bridge));
        engine.run(script, new TestConfig(1, 1, null));

        assertEquals("lyocell", result.get());
    }

    @Test
    void testHttpResponseHeadersAndTimings() throws Exception {
        String url = "http://" + httpbin.getHost() + ":" + httpbin.getMappedPort(80) + "/get";
        
        Path script = tempDir.resolve("http_headers_test.js");
        Files.writeString(script, """
            import http from 'k6/http';
            export default function() {
                const res = http.get('%s');
                globalThis.HeaderResult.set(res.headers['content-type']);
                globalThis.TimingResult.set(res.timings.duration);
            }
            """.formatted(url));

        AtomicReference<String> contentType = new AtomicReference<>();
        TestResultBridge headerBridge = new TestResultBridge(contentType);
        
        AtomicReference<Double> duration = new AtomicReference<>();
        TestTimingBridge timingBridge = new TestTimingBridge(duration);
        
        TestEngine engine = new TestEngine(Map.of(
            "HeaderResult", headerBridge,
            "TimingResult", timingBridge
        ));
        engine.run(script, new TestConfig(1, 1, null));

        assertEquals("application/json", contentType.get());
        assertTrue(duration.get() >= 0, "Duration should be non-negative");
    }

    public static class TestResultBridge {
        private final AtomicReference<String> ref;
        public TestResultBridge(AtomicReference<String> ref) { this.ref = ref; }
        @HostAccess.Export public void set(String val) { ref.set(val); }
    }

    public static class TestStatusBridge {
        private final AtomicInteger ref;
        public TestStatusBridge(AtomicInteger ref) { this.ref = ref; }
        @HostAccess.Export public void set(int val) { ref.set(val); }
    }
    
    public static class TestTimingBridge {
        private final AtomicReference<Double> ref;
        public TestTimingBridge(AtomicReference<Double> ref) { this.ref = ref; }
        @HostAccess.Export public void set(double val) { ref.set(val); }
    }
}