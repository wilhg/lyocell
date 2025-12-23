package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.TestEngine;
import com.wilhg.lyocell.metrics.MetricsCollector;
import org.graalvm.polyglot.Context;

/**
 * Context provided to modules during installation.
 */
public record ModuleContext(
    MetricsCollector metricsCollector,
    TestEngine testEngine
) {}
