package com.wilhg.lyocell.engine;

import java.util.concurrent.atomic.AtomicBoolean;

public record ExecutionContext(int vuId, int iteration, AtomicBoolean failed) {
    public static final ScopedValue<ExecutionContext> CURRENT = ScopedValue.newInstance();

    public ExecutionContext(int vuId) {
        this(vuId, 0, new AtomicBoolean(false));
    }

    public ExecutionContext(int vuId, int iteration) {
        this(vuId, iteration, new AtomicBoolean(false));
    }

    public static ExecutionContext get() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    public void markFailed() {
        this.failed.set(true);
    }

    public boolean isFailed() {
        return failed.get();
    }
}
