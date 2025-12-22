package com.wilhg.lyocell.metrics;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class SummaryReporter {
    public void report(MetricsCollector collector) {
        System.out.println("\n" + "=".repeat(40));
        System.out.println("          LYOCELL TEST SUMMARY");
        System.out.println("=".repeat(40));

        System.out.println("\n[Execution]");
        long iterations = collector.getCounterValue("iterations");
        System.out.printf("  iterations................: %d\n", iterations);

        System.out.println("\n[Checks]");
        long pass = collector.getCounterValue("checks.pass");
        long fail = collector.getCounterValue("checks.fail");
        long total = pass + fail;
        double rate = total > 0 ? (double) pass / total * 100 : 0;
        System.out.printf("  checks....................: %.2f%% (%d pass, %d fail)\n", rate, pass, fail);

        System.out.println("\n[Trends]");
        for (String name : collector.getTrends().keySet()) {
            MetricSummary summary = collector.getTrendSummary(name);
            System.out.printf("  %s:\n", name);
            System.out.printf("    avg=%-10.2f min=%-10.2f med=%-10.2f max=%-10.2f p(95)=%-10.2f p(99)=%-10.2f\n",
                    summary.avg(), summary.min(), summary.avg(), summary.max(), summary.p95(), summary.p99());
        }
        
        System.out.println("=".repeat(40) + "\n");
    }
}
