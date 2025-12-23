package com.wilhg.lyocell;

public record RequestContext(
    String taskId,
    String correlationId,
    long startTime
) {

  public static final ScopedValue<RequestContext> CURRENT = ScopedValue.newInstance();

  public static RequestContext current() {
    return CURRENT.get();
  }

  public static boolean isPresent() {
    return CURRENT.isBound();
  }
}
