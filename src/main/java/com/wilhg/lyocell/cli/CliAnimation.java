package com.wilhg.lyocell.cli;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CliAnimation implements AutoCloseable {
    private volatile String message;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread animationThread;
    private final AtomicInteger lastMessageLength = new AtomicInteger(0);

    private static final char[] ANIM_CHARS = {'⣷', '⣯', '⣟', '⡿', '⢿', '⣻', '⣽', '⣾'};

    public CliAnimation(String message) {
        this.message = message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public synchronized void printLog(String logMessage) {
        // Clear the current animation line
        System.out.print("\r" + " ".repeat(lastMessageLength.get() + 2) + "\r");
        System.out.println(logMessage);
        // The animation thread will redraw on its next tick
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            animationThread = Thread.startVirtualThread(() -> {
                int i = 0;
                try {
                    while (running.get()) {
                        String currentMessage = message;
                        System.out.printf("\r%s %c", currentMessage, ANIM_CHARS[i % ANIM_CHARS.length]);
                        lastMessageLength.set(currentMessage.length());
                        i++;
                        Thread.sleep(Duration.ofMillis(100));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // Clear the line once animation stops
                    System.out.print("\r" + " ".repeat(lastMessageLength.get() + 2) + "\r");
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
