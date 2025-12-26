package com.wilhg.lyocell.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class McpIntegrationTest {
    private HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final BlockingQueue<String> sseQueue = new LinkedBlockingQueue<>();

    @BeforeEach
    public void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/sse", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("data: \"http://localhost:".getBytes());
                os.write(String.valueOf(server.getAddress().getPort()).getBytes());
                os.write("/post\"\n\n".getBytes());
                os.flush();
                
                while (true) {
                    String msg = sseQueue.take();
                    if (msg.equals("STOP")) break;
                    os.write(("data: " + msg + "\n\n").getBytes());
                    os.flush();
                }
            } catch (Exception e) {
                // ignore
            }
        });

        server.createContext("/post", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
            
            try {
                var node = mapper.readTree(body);
                if (node.has("id")) {
                    Map<String, Object> response = Map.of(
                        "jsonrpc", "2.0",
                        "id", node.get("id").asLong(),
                        "result", Map.of("status", "ok")
                    );
                    sseQueue.put(mapper.writeValueAsString(response));
                }
            } catch (Exception e) {
                // ignore
            }
        });

        server.start();
    }

    @AfterEach
    public void teardown() {
        sseQueue.offer("STOP");
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testMcpModuleBridge() throws Exception {
        MetricsCollector metricsCollector = new MetricsCollector();
        TestEngine engine = new TestEngine(Collections.emptyMap(), Collections.emptyList());
        
        try (JsEngine jsEngine = new JsEngine(metricsCollector, engine)) {
            int port = server.getAddress().getPort();
            String script = """
                import mcp from 'lyocell/mcp';
                import { check, sleep } from 'lyocell';
                
                export default function() {
                    const client = mcp.connect('http://localhost:%d/sse');
                    
                    // Call a tool
                    const res = client.callTool('test-tool', { arg: 1 });
                    
                    check(res, {
                        'status is ok': (r) => r.status === 'ok'
                    });

                    // Send a request from server to client manually
                    // In a real scenario, the server would send this.
                    // We can't easily trigger the server from here, 
                    // but we can test the tool call response which we just did.
                    
                    client.close();
                }
                """.formatted(port);
            
            Source source = Source.newBuilder("js", script, "test.js")
                    .mimeType("application/javascript+module")
                    .build();
            Value exports = jsEngine.eval(source);
            
            if (exports.hasMember("default")) {
                exports.getMember("default").execute();
            }
            
            assertEquals(1, metricsCollector.getCounterValue("checks.pass"));
            assertEquals(0, metricsCollector.getCounterValue("checks.fail"));
        }
    }
}
