package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.JsEngine;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TimersModule implements LyocellModule {
    private final AtomicLong timerIdCounter = new AtomicLong();
    private final ConcurrentHashMap<Long, Thread> activeTimers = new ConcurrentHashMap<>();
    private JsEngine jsEngine;

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
        this.jsEngine = moduleContext.jsEngine();
        context.getBindings("js").putMember("LyocellTimers", this);
    }

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
                    jsEngine.executeAsync(() -> {
                        if (activeTimers.containsKey(id)) {
                            callback.execute(args);
                        }
                    });
                }
            } catch (InterruptedException e) {
                // Timer cancelled
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
                        jsEngine.executeAsync(() -> {
                            if (activeTimers.containsKey(id)) {
                                callback.execute(args);
                            }
                        });
                    } else {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                // Timer cancelled
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

