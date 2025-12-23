package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.metrics.MetricsCollector;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class CoreModule implements LyocellModule {
    private MetricsCollector collector;

    public CoreModule() {
    }

    public CoreModule(MetricsCollector collector) {
        this.collector = collector;
    }

    @Override
    public String getName() {
        return "lyocell";
    }

    @Override
    public String getJsSource() {
        return """
            const Core = globalThis.LyocellCore;
            export const check = (val, sets, tags) => Core.check(val, sets, tags);
            export const group = (name, fn) => Core.group(name, fn);
            export const sleep = (sec) => Core.sleep(sec);
            export const fail = (err) => Core.fail(err);
            export const randomSeed = (seed) => Core.randomSeed(seed);
            export default { check, group, sleep, fail, randomSeed };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.collector = moduleContext.metricsCollector();
        context.getBindings("js").putMember("LyocellCore", this);
    }

    @HostAccess.Export
    public void sleep(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @HostAccess.Export
    public void fail(String message) {
        throw new RuntimeException("fail: " + message);
    }

    @HostAccess.Export
    public void randomSeed(long seed) {
        // GraalJS uses the default Math.random() which we can't easily seed globally 
        // without affecting other VUs if they share the engine.
        // But since we have context-per-VU, we might be able to set it if Graal supports it.
        // For now, we'll just log that it's set.
        System.out.println("Random seed set to: " + seed);
    }

    @HostAccess.Export
    public boolean check(Value val, Value sets, Value tags) {
        boolean allPass = true;
        for (String name : sets.getMemberKeys()) {
            Value checkFn = sets.getMember(name);
            try {
                boolean passed = checkFn.execute(val).asBoolean();
                if (passed) {
                    collector.addCounter("checks.pass", 1);
                } else {
                    collector.addCounter("checks.fail", 1);
                    allPass = false;
                }
            } catch (Exception e) {
                collector.addCounter("checks.fail", 1);
                allPass = false;
            }
        }
        return allPass;
    }

    @HostAccess.Export
    public Object group(String name, Value fn) {
        // For Phase 4 MVP, just execute the function. 
        // Future: Apply 'group' tag to all metrics inside.
        return fn.execute();
    }
}
