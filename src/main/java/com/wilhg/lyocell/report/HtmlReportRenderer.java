package com.wilhg.lyocell.report;

import com.wilhg.lyocell.metrics.MetricSummary;
import com.wilhg.lyocell.metrics.MetricsCollector;
import io.micrometer.core.instrument.Meter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class HtmlReportRenderer {

    public void generate(MetricsCollector collector, String outputPath) {
        String html = renderHtml(collector);
        try {
            Files.writeString(Paths.get(outputPath), html);
            System.out.println("\nHTML Report generated at: " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to write HTML report: " + e.getMessage());
        }
    }

    private String renderHtml(MetricsCollector collector) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Lyocell Test Report</title>
                %s
            </head>
            <body>
                <div class="container">
                    %s
                    %s
                    %s
                    %s
                </div>
            </body>
            </html>
            """.formatted(
                renderStyles(),
                renderHeader(),
                renderSummaryCards(collector),
                renderCharts(collector),
                renderDetailedTable(collector)
        );
    }

    private String renderStyles() {
        return """
            <style>
                :root {
                    --primary: #0061ff;
                    --bg: #f5f7fa;
                    --card-bg: #ffffff;
                    --text: #333;
                    --border: #e1e4e8;
                    --success: #2ecc71;
                    --error: #e74c3c;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif;
                    background: var(--bg);
                    color: var(--text);
                    line-height: 1.6;
                    margin: 0;
                    padding: 20px;
                }
                .container { max-width: 960px; margin: 0 auto; }
                h1 { margin-bottom: 20px; color: #1a1a1a; font-weight: 700; }
                
                .grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                    gap: 20px;
                    margin-bottom: 20px;
                }
                
                .card {
                    background: var(--card-bg);
                    padding: 24px;
                    border-radius: 12px;
                    box-shadow: 0 2px 5px rgba(0,0,0,0.05);
                    border: 1px solid var(--border);
                }
                .card h2 {
                    margin-top: 0;
                    font-size: 1.1rem;
                    color: #555;
                    border-bottom: 1px solid var(--border);
                    padding-bottom: 12px;
                    margin-bottom: 20px;
                    font-weight: 600;
                }
                
                /* Metric Value Styles */
                .metric-big { font-size: 3rem; font-weight: 800; color: var(--primary); line-height: 1; }
                .metric-label { font-size: 0.85rem; color: #888; text-transform: uppercase; letter-spacing: 1px; margin-top: 5px; font-weight: 600; }

                /* Bar Chart (CSS-only) */
                .bar-chart { display: flex; height: 200px; align-items: flex-end; gap: 12px; padding-top: 20px; }
                .bar-group { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; justify-content: flex-end; }
                .bar {
                    width: 100%;
                    background: var(--primary);
                    border-radius: 6px 6px 0 0;
                    position: relative;
                    min-height: 4px;
                    transition: height 0.3s ease;
                }
                .bar:hover { opacity: 0.9; }
                .bar-label { font-size: 0.7rem; margin-top: 8px; color: #666; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100px; }
                .bar-value { font-size: 0.75rem; margin-bottom: 5px; font-weight: bold; color: #333; }
                
                /* Table */
                table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                th { text-align: left; padding: 12px; border-bottom: 2px solid var(--border); font-size: 0.85rem; color: #555; font-weight: 700; }
                td { text-align: left; padding: 12px; border-bottom: 1px solid var(--border); font-family: 'SF Mono', Consolas, monospace; font-size: 0.9rem; }
                tr:last-child td { border-bottom: none; }
                
                /* Pie Chart */
                .pie-container { display: flex; align-items: center; gap: 30px; }
                .pie-chart {
                    width: 140px; height: 140px;
                    border-radius: 50%;
                    background: var(--primary); /* Fallback */
                    position: relative;
                }
                .pie-center {
                    position: absolute;
                    top: 50%; left: 50%;
                    transform: translate(-50%, -50%);
                    background: var(--card-bg);
                    width: 90px; height: 90px;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-weight: 800;
                    font-size: 1.5rem;
                    color: #333;
                    box-shadow: inset 0 0 10px rgba(0,0,0,0.05);
                }
                .legend-item { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 0.9rem; color: #555; }
                .dot { width: 12px; height: 12px; border-radius: 4px; }
            </style>
            """;
    }

    private String renderHeader() {
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(java.time.ZoneId.of("UTC"));
            
        return """
            <header style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px;">
                <h1 style="margin:0;">Lyocell Report</h1>
                <div style="color: #888; font-size: 0.9rem; background: #fff; padding: 8px 16px; border-radius: 20px; border: 1px solid #eee;">
                    Generated: %s
                </div>
            </header>
            """.formatted(formatter.format(Instant.now()));
    }

    private String renderSummaryCards(MetricsCollector collector) {
        long iterations = collector.getCounterValue("iterations");
        long failedIterations = collector.getCounterValue("iterations_failed");
        long checksPass = collector.getCounterValue("checks.pass");
        long checksFail = collector.getCounterValue("checks.fail");
        
        long totalChecks = checksPass + checksFail;
        double passRate = totalChecks > 0 ? (double)checksPass / totalChecks * 100 : 0;
        
        // Pass rate for gradient (avoiding double % escape issues by using string format directly)
        String gradient = String.format("conic-gradient(var(--success) 0%% %.1f%%, var(--error) 0%% 100%%)", passRate);

        return """
            <div class="grid">
                <div class="card">
                    <h2>Iterations</h2>
                    <div style="display: flex; align-items: baseline; gap: 10px;">
                        <div class="metric-big">%d</div>
                        <div style="color: %s; font-weight: bold;">%s</div>
                    </div>
                    <div class="metric-label">Total Executions</div>
                </div>
                
                <div class="card">
                    <h2>Checks & Assertions</h2>
                    <div class="pie-container">
                        <div class="pie-chart" style="background: %s">
                             <div class="pie-center">%.0f%%</div>
                        </div>
                        <div class="legend">
                            <div class="legend-item"><span class="dot" style="background: var(--success)"></span> <strong>%d</strong> Passed</div>
                            <div class="legend-item"><span class="dot" style="background: var(--error)"></span> <strong>%d</strong> Failed</div>
                        </div>
                    </div>
                </div>
            </div>
            """.formatted(
                iterations,
                failedIterations > 0 ? "var(--error)" : "#ccc",
                failedIterations > 0 ? failedIterations + " Failed" : "All Successful",
                gradient,
                passRate,
                checksPass, checksFail
            );
    }

    private String renderCharts(MetricsCollector collector) {
        List<MetricSummary> trendSummaries = collector.getRegistry().getMeters().stream()
                .filter(m -> m.getId().getType() == Meter.Type.DISTRIBUTION_SUMMARY)
                .map(m -> collector.getTrendSummary(m.getId().getName()))
                .sorted(Comparator.comparingDouble(MetricSummary::p95).reversed())
                .limit(6)
                .collect(Collectors.toList());

        if (trendSummaries.isEmpty()) return "";

        double maxP95 = trendSummaries.stream().mapToDouble(MetricSummary::p95).max().orElse(1.0);
        
        StringBuilder bars = new StringBuilder();
        // Re-iterate registry to match names (inefficient but safe for simple CLI tool)
        // Better: Store pairs
         List<Meter> sortedMeters = collector.getRegistry().getMeters().stream()
                .filter(m -> m.getId().getType() == Meter.Type.DISTRIBUTION_SUMMARY)
                .sorted((m1, m2) -> Double.compare(
                        collector.getTrendSummary(m2.getId().getName()).p95(),
                        collector.getTrendSummary(m1.getId().getName()).p95()
                ))
                .limit(6)
                .toList();

        for (Meter meter : sortedMeters) {
            String name = meter.getId().getName();
            MetricSummary s = collector.getTrendSummary(name);
            double heightPct = (s.p95() / maxP95) * 100;
            
            bars.append(String.format(
                """
                <div class=\"bar-group\">
                    <div class=\"bar-value\">%.0f</div>
                    <div class=\"bar\" style=\"height: %.1f%%;\"></div>
                    <div class=\"bar-label\" title=\"%s\">%s</div>
                </div>
                """, s.p95(), Math.max(heightPct, 2), name, name));
        }

        return """
            <div class="card">
                <h2>Response Time Overview (p95 ms)</h2>
                <div class="bar-chart">
                    %s
                </div>
            </div>
            <br>
            """.formatted(bars.toString());
    }

    private String renderDetailedTable(MetricsCollector collector) {
        StringBuilder rows = new StringBuilder();
        
        for (Meter meter : collector.getRegistry().getMeters()) {
             if (meter.getId().getType() == Meter.Type.DISTRIBUTION_SUMMARY) {
                String name = meter.getId().getName();
                MetricSummary s = collector.getTrendSummary(name);
                rows.append(String.format(
                    """
                    <tr>
                        <td style=\"font-weight: 600;\">%s</td>
                        <td>%.2f</td>
                        <td>%.2f</td>
                        <td>%.2f</td>
                        <td>%.2f</td>
                        <td>%d</td>
                    </tr>
                    """, name, s.avg(), s.max(), s.p95(), s.p99(), s.count()));
            }
        }

        return """
            <div class="card">
            <h2>Detailed Metrics</h2>
            <table>
                <thead>
                    <tr>
                        <th>Metric Name</th>
                        <th>Avg (ms)</th>
                        <th>Max (ms)</th>
                        <th>p95 (ms)</th>
                        <th>p99 (ms)</th>
                        <th>Count</th>
                    </tr>
                </thead>
                <tbody>
                    %s
                </tbody>
            </table>
            </div>
            """.formatted(rows.toString());
    }
}
