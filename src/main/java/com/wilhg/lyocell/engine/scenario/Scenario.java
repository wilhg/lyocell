package com.wilhg.lyocell.engine.scenario;

public record Scenario(
    String name,
    ExecutorConfig executor,
    String exec // Function to execute, defaults to "default"
) {
    public Scenario(String name, ExecutorConfig executor) {
        this(name, executor, "default");
    }
}
