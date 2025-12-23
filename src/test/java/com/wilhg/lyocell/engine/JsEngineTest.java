package com.wilhg.lyocell.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JsEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void testBasicScriptExecution() throws IOException {
        Path script = tempDir.resolve("test.js");
        Files.writeString(script, "console.log('test');");

        try (JsEngine engine = new JsEngine()) {
            assertDoesNotThrow(() -> engine.runScript(script));
        }
    }

    @Test
    void testK6HttpImport() throws IOException {
        Path script = tempDir.resolve("http_test.js");
        Files.writeString(script, """
            import http from 'lyocell/http';
            export default function() {
                // We just check if 'http' is defined and has 'get'
                if (typeof http.get !== 'function') throw new Error('http.get is not a function');
            }
            """);

        try (JsEngine engine = new JsEngine()) {
            assertDoesNotThrow(() -> engine.runScript(script));
        }
    }

    @Test
    void testSleepInCore() throws IOException {
        Path script = tempDir.resolve("sleep_test.js");
        Files.writeString(script, """
            import { sleep } from 'lyocell';
            sleep(0.1);
            """);

        try (JsEngine engine = new JsEngine()) {
            long start = System.currentTimeMillis();
            engine.runScript(script);
            long duration = System.currentTimeMillis() - start;
            assertTrue(duration >= 100, "Sleep should have lasted at least 100ms");
        }
    }
}
