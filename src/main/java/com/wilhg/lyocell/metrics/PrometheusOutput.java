package com.wilhg.lyocell.metrics;

import com.wilhg.lyocell.engine.OutputConfig;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PrometheusOutput {
    public static PrometheusMeterRegistry createRegistry(OutputConfig config) {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        
        String target = config.target();
        if (target != null && (target.startsWith("http://") || target.startsWith("https://"))) {
            setupPushgateway(registry, target);
        } else {
             System.err.println("Warning: Invalid Prometheus URL provided: '" + target + "'. " +
                 "Lyocell only supports Pushgateway (url must start with http:// or https://). Metrics will not be exported.");
        }

        return registry;
    }

    private static void setupPushgateway(PrometheusMeterRegistry registry, String urlString) {
        String finalUrl = urlString;
        String authHeader = null;

        try {
            URI uri = URI.create(urlString);
            if (uri.getUserInfo() != null) {
                String userInfo = uri.getUserInfo();
                String token = Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));
                authHeader = "Basic " + token;
                
                // Reconstruct URL without user info
                finalUrl = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse Prometheus URL for Basic Auth: " + e.getMessage());
        }

        // Ensure URL ends with job path if not present
        if (!finalUrl.contains("/metrics/job/")) {
            if (!finalUrl.endsWith("/")) finalUrl += "/";
            finalUrl += "metrics/job/lyocell";
        }

        final String targetUrl = finalUrl;
        final String finalAuthHeader = authHeader;
        JdkHttpSender sender = new JdkHttpSender();

        Thread.ofVirtual().name("prometheus-push").start(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // Push every 5 seconds
                    push(registry, sender, targetUrl, finalAuthHeader);
                } catch (InterruptedException e) {
                    // Try one last push before exiting
                    push(registry, sender, targetUrl, finalAuthHeader);
                    break;
                } catch (Exception e) {
                    System.err.println("Failed to push to Prometheus Pushgateway: " + e.getMessage());
                }
            }
        });
        
        System.out.println("Prometheus Pushgateway configured at " + targetUrl + (authHeader != null ? " (with Basic Auth)" : ""));
    }

    private static void push(PrometheusMeterRegistry registry, JdkHttpSender sender, String url, String authHeader) {
        try {
            var request = sender.post(url)
                .withContent("text/plain", registry.scrape());
            
            if (authHeader != null) {
                request.withHeader("Authorization", authHeader);
            }
            
            request.send();
        } catch (Throwable e) {
            // Log or ignore
        }
    }
}
