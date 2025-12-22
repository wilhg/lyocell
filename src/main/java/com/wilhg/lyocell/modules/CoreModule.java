package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.metrics.MetricsCollector;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class CoreModule {
    private final MetricsCollector collector;

    public CoreModule(MetricsCollector collector) {
        this.collector = collector;
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
