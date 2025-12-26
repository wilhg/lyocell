package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TimersModule implements LyocellModule {
    private final AtomicLong timerIdCounter = new AtomicLong();
    private final ConcurrentHashMap<Long, Thread> activeTimers = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "lyocell/timers";
    }

    @Override
    public String getJsSource() {
        return """
            const Timers = globalThis.LyocellTimers;
            export const setTimeout = (callback, delay, ...args) => Timers.setTimeout(callback, delay, args);
            export const clearTimeout = (id) => Timers.clearTimeout(id);
            export const setInterval = (callback, delay, ...args) => Timers.setInterval(callback, delay, args);
            export const clearInterval = (id) => Timers.clearInterval(id);
            export default { setTimeout, clearTimeout, setInterval, clearInterval };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.context = context;
        context.getBindings("js").putMember("LyocellTimers", this);
    }

    private Context context;

    @Override
    public void close() {
        for (Thread thread : activeTimers.values()) {
            thread.interrupt();
        }
        activeTimers.clear();
    }

    @HostAccess.Export
    public long setTimeout(Value callback, long delay, Value args) {
        long id = timerIdCounter.incrementAndGet();
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delay);
                if (activeTimers.containsKey(id)) {
                    context.enter();
                    try {
                        callback.execute(args);
                    } finally {
                        context.leave();
                    }
                }
            } catch (InterruptedException e) {
                // Timer cancelled
            } catch (Exception e) {
                // Potential concurrent access if VU is running, but let's try
            } finally {
                activeTimers.remove(id);
            }
        });
        activeTimers.put(id, thread);
        return id;
    }

    @HostAccess.Export
    public void clearTimeout(long id) {
        Thread thread = activeTimers.remove(id);
        if (thread != null) {
            thread.interrupt();
        }
    }

    @HostAccess.Export
    public long setInterval(Value callback, long delay, Value args) {
        long id = timerIdCounter.incrementAndGet();
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(delay);
                    if (activeTimers.containsKey(id)) {
                        context.enter();
                        try {
                            callback.execute(args);
                        } finally {
                            context.leave();
                        }
                    } else {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                // Timer cancelled
            } catch (Exception e) {
                // Potential concurrent access
            } finally {
                activeTimers.remove(id);
            }
        });
        activeTimers.put(id, thread);
        return id;
    }

    @HostAccess.Export
    public void clearInterval(long id) {
        clearTimeout(id);
    }
}

