package com.wilhg.lyocell.report;

import com.wilhg.lyocell.metrics.MetricSummary;
import com.wilhg.lyocell.metrics.MetricsCollector;
import com.wilhg.lyocell.metrics.TimeSeriesData;
import io.micrometer.core.instrument.Meter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HtmlReportRenderer {

    public void generate(MetricsCollector collector, List<TimeSeriesData> timelineData, String outputPath) {
        String html = renderHtml(collector, timelineData);
        try {
            Files.writeString(Paths.get(outputPath), html);
            System.out.println("\nHTML Report generated at: " + outputPath);
        } catch (IOException e) {
            System.err.println("Failed to write HTML report: " + e.getMessage());
        }
    }

    private String renderHtml(MetricsCollector collector, List<TimeSeriesData> timelineData) {
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
                    %s
                </div>
            </body>
            </html>
            """.formatted(
                renderStyles(),
                renderHeader(),
                renderSummaryCards(collector),
                renderCharts(collector, timelineData),
                renderDetailedTable(collector),
                renderFooter()
        );
    }

    private String renderStyles() {
        return """
            <style>
                :root {
                    --primary: #0061ff;
                    --primary-gradient: linear-gradient(180deg, #0061ff 0%, #004ecc 100%);
                    --bg: #f5f7fa;
                    --card-bg: #ffffff;
                    --text: #333;
                    --border: #e1e4e8;
                    --success: #2ecc71;
                    --error: #e74c3c;
                    --warning: #f39c12;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    background: var(--bg);
                    color: var(--text);
                    line-height: 1.6;
                    margin: 0;
                    padding: 20px;
                }
                .container { max-width: 1024px; margin: 0 auto; }
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
                    box-shadow: 0 4px 6px rgba(0,0,0,0.02);
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

                /* Bar Chart (old, for response times) */
                .bar-chart { display: flex; height: 220px; align-items: flex-end; gap: 16px; padding-top: 20px; }
                .bar-group { flex: 1; display: flex; flex-direction: column; align-items: center; height: 100%; justify-content: flex-end; position: relative; }
                .bar {
                    width: 100%;
                    background: var(--primary-gradient);
                    border-radius: 6px 6px 0 0;
                    position: relative;
                    min-height: 4px;
                    transition: all 0.2s ease;
                    opacity: 0.9;
                }
                .bar:hover { opacity: 1; transform: scaleY(1.02); transform-origin: bottom; }
                
                .bar-label { font-size: 0.75rem; margin-top: 8px; color: #666; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 120px; font-weight: 500; }
                .bar-value { font-size: 0.8rem; margin-bottom: 5px; font-weight: bold; color: #333; }
                
                /* Timeline Chart (new) */
                .timeline-chart {
                    display: flex;
                    align-items: flex-end;
                    height: 250px;
                    gap: 2px;
                    padding-top: 10px;
                    border-bottom: 1px solid var(--border);
                    position: relative;
                    overflow-x: auto;
                    padding-bottom: 30px; /* Space for labels */
                }
                .timeline-bar-wrapper {
                    display: flex;
                    flex-direction: column-reverse; /* Stack bars from bottom up */
                    width: 15px; /* Fixed width for each time bucket */
                    position: relative;
                    justify-content: flex-start;
                }
                .timeline-bar {
                    width: 100%;
                    position: relative;
                    min-height: 1px;
                }
                .timeline-bar.success { background: var(--success); opacity: 0.8; }
                .timeline-bar.failure { background: var(--error); opacity: 0.8; }
                .timeline-bar:hover { opacity: 1; }
                
                .timeline-label {
                    position: absolute;
                    bottom: -25px; /* Below the bars */
                    left: 50%;
                    transform: translateX(-50%);
                    font-size: 0.7rem;
                    color: #777;
                    white-space: nowrap;
                }
                .timeline-tooltip {
                    position: absolute;
                    bottom: 100%; /* Above the bar */
                    left: 50%;
                    transform: translateX(-50%);
                    background: rgba(0,0,0,0.8);
                    color: #fff;
                    padding: 4px 8px;
                    border-radius: 4px;
                    font-size: 0.7rem;
                    white-space: nowrap;
                    visibility: hidden;
                    opacity: 0;
                    transition: opacity 0.2s, visibility 0.2s;
                    z-index: 10;
                }
                .timeline-bar-wrapper:hover .timeline-tooltip {
                    visibility: visible;
                    opacity: 1;
                }

                /* Table */
                table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                th { text-align: left; padding: 12px; border-bottom: 2px solid var(--border); font-size: 0.85rem; color: #555; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; }
                td { text-align: left; padding: 12px; border-bottom: 1px solid var(--border); font-family: 'SF Mono', Consolas, monospace; font-size: 0.9rem; color: #444; }
                tr:last-child td { border-bottom: none; }
                tr:hover td { background-color: #fafbfc; }
                
                /* Numeric Columns */
                .num { text-align: right; font-variant-numeric: tabular-nums; }
                
                /* Pie Chart */
                .pie-container { display: flex; align-items: center; gap: 30px; }
                .pie-chart {
                    width: 140px; height: 140px;
                    border-radius: 50%;
                    background: var(--primary);
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
                
                .footer { margin-top: 40px; text-align: center; color: #999; font-size: 0.85rem; padding-bottom: 20px; }
                .footer a { color: #999; text-decoration: none; }
                .footer a:hover { text-decoration: underline; }
            </style>
            """;
    }

    private String renderHeader() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneId.of("UTC"));
            
        return """
            <header style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px;">
                <div style="display: flex; align-items: center; gap: 15px;">
                    <div style="width: 40px; height: 40px; background: linear-gradient(135deg, #0061ff, #00a2ff); border-radius: 8px;"></div>
                    <h1 style="margin:0;">Lyocell Report</h1>
                </div>
                <div style="color: #666; font-size: 0.9rem; background: #fff; padding: 8px 16px; border-radius: 20px; border: 1px solid #e1e4e8; font-weight: 500;">
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
        
        String gradient = String.format(Locale.US, "conic-gradient(var(--success) 0%% %.1f%%, var(--error) 0%% 100%%)", passRate);

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
                failedIterations > 0 ? "var(--error)" : "#aaa",
                failedIterations > 0 ? failedIterations + " Failed" : "All Successful",
                gradient,
                passRate,
                checksPass, checksFail
            );
    }

    private String renderCharts(MetricsCollector collector, List<TimeSeriesData> timelineData) {
        List<MetricSummary> trendSummaries = collector.getRegistry().getMeters().stream()
                .filter(m -> m.getId().getType() == Meter.Type.DISTRIBUTION_SUMMARY)
                .map(m -> collector.getTrendSummary(m.getId().getName()))
                .sorted(Comparator.comparingDouble(MetricSummary::p95).reversed())
                .limit(6)
                .collect(Collectors.toList());

        StringBuilder chartHtml = new StringBuilder();

        if (!timelineData.isEmpty()) {
            chartHtml.append(renderTimelineChart(timelineData));
        }

        if (!trendSummaries.isEmpty()) {
            double maxP95 = trendSummaries.stream().mapToDouble(MetricSummary::p95).max().orElse(1.0);
            
            StringBuilder bars = new StringBuilder();
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
                
                bars.append(String.format(Locale.US,
                    """
                    <div class="bar-group">
                        <div class="bar-value">%.0f</div>
                        <div class="bar" style="height: %.1f%%;"></div>
                        <div class="bar-label" title="%s">%s</div>
                    </div>
                    """, s.p95(), Math.max(heightPct, 2), name, name));
            }

            chartHtml.append("""
                <div class="card">
                    <h2>Response Time Overview (p95 ms)</h2>
                    <div class="bar-chart">
                        %s
                    </div>
                </div>
                """.formatted(bars.toString()));
        }

        return chartHtml.toString();
    }

    private String renderTimelineChart(List<TimeSeriesData> timelineData) {
        if (timelineData.isEmpty()) {
            return "";
        }

        long maxRequestsPerBucket = timelineData.stream()
                .mapToLong(data -> data.successfulRequests() + data.failedRequests())
                .max()
                .orElse(1L);

        StringBuilder barsHtml = new StringBuilder();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("UTC"));

        for (int i = 0; i < timelineData.size(); i++) {
            TimeSeriesData data = timelineData.get(i);
            long totalRequests = data.successfulRequests() + data.failedRequests();
            double successfulHeightPct = (double) data.successfulRequests() / maxRequestsPerBucket * 100;
            double failedHeightPct = (double) data.failedRequests() / maxRequestsPerBucket * 100;
            
            String tooltip = String.format(Locale.US, 
                "Time: %s<br>Success: %d<br>Failed: %d", 
                timeFormatter.format(Instant.ofEpochMilli(data.timestamp())),
                data.successfulRequests(), data.failedRequests()
            );

            barsHtml.append(String.format(Locale.US,
                """
                <div class="timeline-bar-wrapper">
                    <div class="timeline-tooltip">%s</div>
                    <div class="timeline-bar failure" style="height: %.1f%%;" title="Failed: %d"></div>
                    <div class="timeline-bar success" style="height: %.1f%%;" title="Success: %d"></div>
                    %s
                </div>
                """,
                tooltip,
                Math.max(failedHeightPct, data.failedRequests() > 0 ? 1 : 0), data.failedRequests(), // Ensure at least 1px for visibility if > 0
                Math.max(successfulHeightPct, data.successfulRequests() > 0 ? 1 : 0), data.successfulRequests(), // Ensure at least 1px for visibility if > 0
                (i % 10 == 0) ? String.format(Locale.US, "<div class=\"timeline-label\">%s</div>", timeFormatter.format(Instant.ofEpochMilli(data.timestamp()))) : ""
            ));
        }

        return """
            <div class="card">
                <h2>Request Volume Timeline (Reqs/Sec)</h2>
                <div class="timeline-chart">
                    %s
                </div>
            </div>
            <br>
            """.formatted(barsHtml.toString());
    }

    private String renderDetailedTable(MetricsCollector collector) {
        StringBuilder rows = new StringBuilder();
        
        for (Meter meter : collector.getRegistry().getMeters()) {
             if (meter.getId().getType() == Meter.Type.DISTRIBUTION_SUMMARY) {
                String name = meter.getId().getName();
                MetricSummary s = collector.getTrendSummary(name);
                rows.append(String.format(Locale.US,
                    """
                    <tr>
                        <td style="font-weight: 600;">%s</td>
                        <td class="num">%.2f</td>
                        <td class="num">%.2f</td>
                        <td class="num">%.2f</td>
                        <td class="num">%.2f</td>
                        <td class="num">%d</td>
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
                        <th class="num">Avg</th>
                        <th class="num">Max</th>
                        <th class="num">p95</th>
                        <th class="num">p99</th>
                        <th class="num">Count</th>
                    </tr>
                </thead>
                <tbody>
                    %s
                </tbody>
            </table>
            </div>
            """.formatted(rows.toString());
    }
    
    private String renderFooter() {
        return """
            <div class="footer">
                Generated by <strong>Lyocell</strong> - <a href="https://github.com/wilhg/lyocell">View on GitHub</a>
            </div>
            """;
    }
}
