package com.wilhg.lyocell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CliIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testMainExecution() throws Exception {
        Path script = tempDir.resolve("cli_test.js");
        Files.writeString(script, """
            import { Counter } from 'k6/metrics';
            const c = new Counter('cli_counter');
            export default function() {
                c.add(1);
            }
            """);

        // Capture stdout to verify summary
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        // Run with -i 5 (5 iterations)
        int exitCode = Main.run(new String[]{script.toString(), "-i", "5"});

        String output = outContent.toString();
        
        // Reset stdout
        System.setOut(System.out);
        
        assertEquals(0, exitCode, "Exit code should be 0");
        assertTrue(output.contains("iterations................: 5"), "Output should show 5 iterations");
    }
}
