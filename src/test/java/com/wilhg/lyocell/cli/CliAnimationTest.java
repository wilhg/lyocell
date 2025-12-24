package com.wilhg.lyocell.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

public class CliAnimationTest {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private CliAnimation animation;

    @BeforeEach
    void setUp() {
        // Capture System.out for testing output
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        // Restore original System.out
        System.setOut(originalOut);
        // Clean up any running animation
        if (animation != null) {
            animation.close();
        }
    }

    @Test
    void testConstructor() {
        animation = new CliAnimation("Loading");
        assertNotNull(animation);
        // Test that it implements AutoCloseable
        assertTrue(animation instanceof AutoCloseable);
    }

    @Test
    void testStartAndStop() throws InterruptedException {
        animation = new CliAnimation("Processing");

        animation.start();

        // Wait a bit to let animation run
        Thread.sleep(150);

        animation.stop();

        // Check that output was generated
        String output = outputStream.toString();
        assertTrue(output.contains("Processing"), "Should contain the message");
        assertTrue(output.contains("⣷") || output.contains("⣯") || output.contains("⣟") ||
                   output.contains("⡿") || output.contains("⢿") || output.contains("⣻") ||
                   output.contains("⣽") || output.contains("⣾"), "Should contain animation characters");
    }

    @Test
    void testCloseCallsStop() throws InterruptedException {
        animation = new CliAnimation("Testing");
        animation.start();

        // Wait a bit
        Thread.sleep(100);

        animation.close();

        // Verify it can be called multiple times safely (close should be idempotent)
        animation.close();
    }

    @Test
    void testMultipleStartCalls() throws InterruptedException {
        animation = new CliAnimation("Multiple");

        // First start should work
        animation.start();

        // Second start should be safe to call (should not crash)
        animation.start();

        // Should still be able to stop
        animation.stop();
    }

    @Test
    void testMultipleStopCalls() throws InterruptedException {
        animation = new CliAnimation("Multiple");
        animation.start();

        animation.stop();

        // Second stop should be safe to call
        animation.stop();
    }

    @Test
    void testAnimationCharacterCycling() throws InterruptedException {
        animation = new CliAnimation("Cycle");

        animation.start();
        Thread.sleep(250); // Let it cycle through a few characters
        animation.stop();

        String output = outputStream.toString();
        // Should contain multiple different animation characters
        long uniqueChars = output.chars()
                .filter(ch -> "⣷⣯⣟⡿⢿⣻⣽⣾".indexOf(ch) != -1)
                .distinct()
                .count();

        assertTrue(uniqueChars >= 2, "Should cycle through multiple animation characters, found: " + uniqueChars);
    }

    @Test
    void testAnimationTiming() throws InterruptedException {
        animation = new CliAnimation("Timing");

        animation.start();
        Thread.sleep(350); // Should see at least 3 animation frames (100ms each)
        animation.stop();

        String output = outputStream.toString();
        long frameCount = output.chars()
                .filter(ch -> "⣷⣯⣟⡿⢿⣻⣽⣾".indexOf(ch) != -1)
                .count();

        assertTrue(frameCount >= 3, "Should have at least 3 animation frames in 350ms, found: " + frameCount);
    }

    @Test
    void testLineClearingOnStop() throws InterruptedException {
        animation = new CliAnimation("ClearTest");

        animation.start();
        Thread.sleep(150);
        animation.stop();

        String output = outputStream.toString();
        // Should end with clearing sequence (spaces + carriage return)
        assertTrue(output.endsWith("\r" + " ".repeat("ClearTest".length() + 2) + "\r"),
                   "Should clear the line on stop");
    }

    @Test
    void testThreadInterruption() throws InterruptedException {
        animation = new CliAnimation("Interrupt");

        animation.start();

        // The animation thread should handle interruption gracefully
        animation.stop();

        // Wait a bit to ensure cleanup
        Thread.sleep(50);

        // Verify it stopped (by checking no more output is generated)
        String outputBefore = outputStream.toString();
        Thread.sleep(200);
        String outputAfter = outputStream.toString();

        // Output should not grow after stop
        assertEquals(outputBefore.length(), outputAfter.length(),
                    "Animation should stop producing output after stop()");
    }

    @Test
    void testEmptyMessage() throws InterruptedException {
        animation = new CliAnimation("");

        animation.start();
        Thread.sleep(150);
        animation.stop();

        String output = outputStream.toString();
        // Should still work with empty message
        assertTrue(output.length() > 0, "Should produce output even with empty message");
    }

    @Test
    void testLongMessage() throws InterruptedException {
        String longMessage = "This is a very long message for testing purposes";
        animation = new CliAnimation(longMessage);

        animation.start();
        Thread.sleep(150);
        animation.stop();

        String output = outputStream.toString();
        assertTrue(output.contains(longMessage), "Should handle long messages");
        assertTrue(output.contains("\r" + " ".repeat(longMessage.length() + 2) + "\r"),
                   "Should clear line with correct length for long message");
    }


    @Test
    void testAnimationCharactersArray() throws InterruptedException {
        animation = new CliAnimation("Chars");
        animation.start();

        // Let it cycle through all characters (8 chars * 100ms = 800ms)
        Thread.sleep(850);

        animation.stop();

        String output = outputStream.toString();
        // Should contain all 8 animation characters
        char[] expectedChars = {'⣷', '⣯', '⣟', '⡿', '⢿', '⣻', '⣽', '⣾'};
        for (char ch : expectedChars) {
            assertTrue(output.contains(String.valueOf(ch)),
                       "Should contain animation character: " + ch);
        }
    }

    @Test
    void testSetMessage() throws InterruptedException {
        animation = new CliAnimation("Initial");
        animation.start();
        Thread.sleep(150);
        
        animation.setMessage("Updated");
        Thread.sleep(150);
        
        animation.stop();
        
        String output = outputStream.toString();
        assertTrue(output.contains("Initial"), "Should contain initial message");
        assertTrue(output.contains("Updated"), "Should contain updated message");
    }

    @Test
    void testPrintLog() throws InterruptedException {
        animation = new CliAnimation("Animation");
        animation.start();
        Thread.sleep(150);
        
        animation.printLog("Log message 1");
        animation.printLog("Log message 2");
        Thread.sleep(150);
        
        animation.stop();
        
        String output = outputStream.toString();
        assertTrue(output.contains("Log message 1\n"), "Should contain first log message followed by newline");
        assertTrue(output.contains("Log message 2\n"), "Should contain second log message followed by newline");
        assertTrue(output.contains("Animation"), "Should still contain the animation message");
    }
}
