package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.JsEngine;
import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GrpcIntegrationTest {
    private Server server;

    @BeforeEach
    public void setup() throws IOException {
    }

    @AfterEach
    public void teardown() {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testGrpcModuleBridge() throws Exception {
        MetricsCollector metricsCollector = new MetricsCollector();
        TestEngine engine = new TestEngine(Collections.emptyMap(), Collections.emptyList());
        
        try (JsEngine jsEngine = new JsEngine(metricsCollector, engine)) {
            String script = """
                import grpc from 'lyocell/net/grpc';
                import { check } from 'lyocell';
                
                export default function() {
                    const client = new grpc.Client();
                    
                    // Test connect
                    client.connect('localhost:50051', { plaintext: true });
                    
                    // Test invoke
                    const res = client.invoke('hello.HelloService/SayHello', { name: 'world' });
                    
                    check(res, {
                        'status is OK': (r) => r.status === 0,
                        'has message': (r) => r.message !== undefined,
                        'correct reply': (r) => r.message.reply === 'Hello world'
                    });
                    
                    client.close();
                }
                """;
            
            Source source = Source.newBuilder("js", script, "test.js")
                    .mimeType("application/javascript+module")
                    .build();
            Value exports = jsEngine.eval(source);
            
            // Execute the default function
            if (exports.hasMember("default")) {
                exports.getMember("default").execute();
            }
            
            // Verify metrics/checks
            assertEquals(1, metricsCollector.getCounterValue("checks.pass"));
            assertEquals(0, metricsCollector.getCounterValue("checks.fail"));
        }
    }
}

