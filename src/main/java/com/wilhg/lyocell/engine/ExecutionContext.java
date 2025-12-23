package com.wilhg.lyocell.engine;

public class ExecutionContext {
    private static final ThreadLocal<ExecutionContext> CURRENT = new ThreadLocal<>();

    private final int vuId;
    private final int iteration;

    public ExecutionContext(int vuId) {
        this(vuId, 0);
    }

    public ExecutionContext(int vuId, int iteration) {
        this.vuId = vuId;
        this.iteration = iteration;
    }

    public static void set(ExecutionContext context) {
        CURRENT.set(context);
    }

    public static ExecutionContext get() {
        return CURRENT.get();
    }

    public static void remove() {
        CURRENT.remove();
    }

    public int getVuId() {
        return vuId;
    }

    public int getIteration() {
        return iteration;
    }
}
