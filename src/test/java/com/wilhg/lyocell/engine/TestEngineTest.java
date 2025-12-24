package com.wilhg.lyocell.engine;

import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TestEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void testFormatDuration() {
        TestEngine engine = new TestEngine(Collections.emptyList());
        assertEquals("0s", engine.formatDuration(0));
        assertEquals("5s", engine.formatDuration(5000));
        assertEquals("59s", engine.formatDuration(59000));
        assertEquals("1m", engine.formatDuration(60000));
        assertEquals("1m1s", engine.formatDuration(61000));
        assertEquals("2m", engine.formatDuration(120000));
        assertEquals("1h1m", engine.formatDuration(3660000)); // Should it handle hours? The current implementation doesn't seem to explicitly handle hours, it just does minutes. Let's check.
    }

    public static class TestHelper {
        private final AtomicInteger counter;

        public TestHelper(AtomicInteger counter) {
            this.counter = counter;
        }

        @HostAccess.Export
        public void increment() {
            counter.incrementAndGet();
        }
    }
}
