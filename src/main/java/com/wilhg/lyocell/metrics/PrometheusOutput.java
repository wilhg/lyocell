package com.wilhg.lyocell.metrics;

import com.sun.net.httpserver.HttpServer;
import com.wilhg.lyocell.engine.OutputConfig;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class PrometheusOutput {
    public static PrometheusMeterRegistry createRegistry(OutputConfig config) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        
        String target = config.target();
        if (target.startsWith("http://") || target.startsWith("https://")) {
            // Pushgateway mode
            setupPushgateway(registry, target);
        } else {
            // Scrape mode (default)
            setupScrapeEndpoint(registry, target);
        }

        return registry;
    }

    private static void setupScrapeEndpoint(PrometheusMeterRegistry registry, String target) {
        int port = 9090;
        if (target != null && !target.isEmpty()) {
            try {
                port = Integer.parseInt(target);
            } catch (NumberFormatException e) {
                // Keep default port
            }
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", httpExchange -> {
                String response = registry.scrape();
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                httpExchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            Thread.ofVirtual().name("prometheus-server").start(server::start);
            System.out.println("Prometheus scrape endpoint started at http://localhost:" + port + "/metrics");
        } catch (IOException e) {
            System.err.println("Failed to start Prometheus scrape endpoint: " + e.getMessage());
        }
    }

    private static void setupPushgateway(PrometheusMeterRegistry registry, String url) {
        // Ensure URL ends with job path if not present
        String pushUrl = url;
        if (!pushUrl.contains("/metrics/job/")) {
            if (!pushUrl.endsWith("/")) pushUrl += "/";
            pushUrl += "metrics/job/lyocell";
        }

        final String finalUrl = pushUrl;
        JdkHttpSender sender = new JdkHttpSender();

        Thread.ofVirtual().name("prometheus-push").start(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // Push every 5 seconds
                    push(registry, sender, finalUrl);
                } catch (InterruptedException e) {
                    // Try one last push before exiting
                    push(registry, sender, finalUrl);
                    break;
                } catch (Exception e) {
                    System.err.println("Failed to push to Prometheus Pushgateway: " + e.getMessage());
                }
            }
        });
        
        System.out.println("Prometheus Pushgateway configured at " + finalUrl);
    }

    private static void push(PrometheusMeterRegistry registry, JdkHttpSender sender, String url) {
        try {
            sender.post(url)
                .withContent("text/plain", registry.scrape())
                .send();
        } catch (Throwable e) {
            // Log or ignore
        }
    }
}
