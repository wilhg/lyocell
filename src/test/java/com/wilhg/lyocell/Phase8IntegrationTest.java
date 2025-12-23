package com.wilhg.lyocell;

import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class Phase8IntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testParallelScenarios() throws Exception {
        Path script = tempDir.resolve("scenarios.js");
        Files.writeString(script, """
            import { sleep } from 'k6';
            const helper = globalThis.TestHelper;

            export const options = {
                scenarios: {
                    per_vu: {
                        executor: 'per-vu-iterations',
                        vus: 2,
                        iterations: 2,
                        exec: 'perVuFunc'
                    },
                    shared: {
                        executor: 'shared-iterations',
                        vus: 2,
                        iterations: 5,
                        exec: 'sharedFunc'
                    }
                }
            };

            export function perVuFunc() {
                helper.increment('per_vu');
            }

            export function sharedFunc() {
                helper.increment('shared');
            }
            """);

        TestHelper helper = new TestHelper();
        TestEngine engine = new TestEngine(Map.of("TestHelper", helper));
        
        // vus and iterations are defaults if no scenarios, but here we have scenarios
        TestConfig config = new TestConfig(1, 1);
        engine.run(script, config);

        // per_vu: 2 vus * 2 iterations = 4
        assertEquals(4, helper.getCount("per_vu"));
        // shared: 5 total iterations
        assertEquals(5, helper.getCount("shared"));
    }

    @Test
    void testConstantVus() throws Exception {
        Path script = tempDir.resolve("constant_vus.js");
        Files.writeString(script, """
            import { sleep } from 'k6';
            const helper = globalThis.TestHelper;

            export const options = {
                scenarios: {
                    constant: {
                        executor: 'constant-vus',
                        vus: 2,
                        duration: '1s'
                    }
                }
            };

            export default function() {
                helper.increment('constant');
                sleep(0.1);
            }
            """);

        TestHelper helper = new TestHelper();
        TestEngine engine = new TestEngine(Map.of("TestHelper", helper));
        
        engine.run(script, new TestConfig(1, 1));

        // In 1 second with 0.1s sleep, each VU should do ~10 iterations. 2 VUs -> ~20.
        // We'll just check if it's > 5 to be safe.
        assertTrue(helper.getCount("constant") > 5);
    }

    public static class TestHelper {
        private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

        @HostAccess.Export
        public void increment(String key) {
            counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        }

        public int getCount(String key) {
            AtomicInteger counter = counters.get(key);
            return counter != null ? counter.get() : 0;
        }
    }
}
