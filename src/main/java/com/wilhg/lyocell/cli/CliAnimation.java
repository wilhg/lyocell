package com.wilhg.lyocell.cli;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class CliAnimation implements AutoCloseable {
    private final String message;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread animationThread;

    private static final char[] ANIM_CHARS = {'⣷', '⣯', '⣟', '⡿', '⢿', '⣻', '⣽', '⣾'};

    public CliAnimation(String message) {
        this.message = message;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            animationThread = Thread.startVirtualThread(() -> {
                int i = 0;
                try {
                    while (running.get()) {
                        System.out.printf("\r%s %c", message, ANIM_CHARS[i % ANIM_CHARS.length]);
                        i++;
                        Thread.sleep(Duration.ofMillis(100));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // Clear the line once animation stops
                    System.out.print("\r" + " ".repeat(message.length() + 2) + "\r");
                }
            });
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (animationThread != null) {
                animationThread.interrupt();
                try {
                    animationThread.join(); // Wait for the animation thread to finish
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public void close() {
        stop();
    }
}
