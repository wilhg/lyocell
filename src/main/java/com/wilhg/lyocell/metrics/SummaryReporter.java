package com.wilhg.lyocell.metrics;

import io.micrometer.core.instrument.Meter;

import java.util.stream.Collectors;

public class SummaryReporter {
    public void report(MetricsCollector collector) {
        System.out.println("\n" + "=".repeat(40));
        System.out.println("          LYOCELL TEST SUMMARY");
        System.out.println("=".repeat(40));

        System.out.println("\n[Execution]");
        long iterations = collector.getCounterValue("iterations");
        long iterationsFailed = collector.getCounterValue("iterations_failed");
        long iterationsPassed = iterations - iterationsFailed;
        double iterationSuccessRate = iterations > 0 ? (double) iterationsPassed / iterations * 100 : 0;
        System.out.printf("  iterations................: %.2f%% (%d pass, %d fail)\n", iterationSuccessRate, iterationsPassed, iterationsFailed);

        System.out.println("\n[Checks]");
        long pass = collector.getCounterValue("checks.pass");
        long fail = collector.getCounterValue("checks.fail");
        long total = pass + fail;
        double rate = total > 0 ? (double) pass / total * 100 : 0;
        System.out.printf("  checks....................: %.2f%% (%d pass, %d fail)\n", rate, pass, fail);

        System.out.println("\n[Trends]");
        var trendNames = collector.getRegistry().getMeters().stream()
                .filter(m -> m.getId().getType() == Meter.Type.DISTRIBUTION_SUMMARY)
                .map(m -> m.getId().getName())
                .collect(Collectors.toSet());

        for (String name : trendNames) {
            MetricSummary summary = collector.getTrendSummary(name);
            System.out.printf("  %s:\n", name);
            System.out.printf("    avg=%-10.2f max=%-10.2f p(95)=%-10.2f p(99)=%-10.2f count=%d\n",
                    summary.avg(), summary.max(), summary.p95(), summary.p99(), summary.count());
        }
        
        System.out.println("=".repeat(40) + "\n");
    }
}