package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public class CoreModule {
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
        // Minimal implementation for MVP
        System.out.println("[Core] Performing check...");
        return true;
    }
}
