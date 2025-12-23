package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.metrics.MetricsCollector;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpModule {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    
    private Context context;
    private final MetricsCollector metricsCollector;

    public HttpModule(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    @HostAccess.Export
    public HttpResponseWrapper get(String url, Value params) {
        return request("GET", url, null, params);
    }

    @HostAccess.Export
    public HttpResponseWrapper post(String url, Object body, Value params) {
        return request("POST", url, body, params);
    }

    private HttpResponseWrapper request(String method, String url, Object body, Value params) {
        Instant start = Instant.now();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url));

            // Set headers
            if (params != null && params.hasMember("headers")) {
                Value headers = params.getMember("headers");
                for (String key : headers.getMemberKeys()) {
                    builder.header(key, headers.getMember(key).asString());
                }
            }

            // Set method and body
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
            if (body != null) {
                bodyPublisher = HttpRequest.BodyPublishers.ofString(body.toString());
            }
            builder.method(method, bodyPublisher);

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            double duration = Duration.between(start, Instant.now()).toMillis();
            
            if (metricsCollector != null) {
                metricsCollector.addTrend("http_req_duration", duration);
            }

            return new HttpResponseWrapper(response, duration, context);
        } catch (Exception e) {
            double duration = Duration.between(start, Instant.now()).toMillis();
            if (metricsCollector != null) {
                metricsCollector.addTrend("http_req_duration", duration);
            }
            return new HttpResponseWrapper(e.getMessage(), duration, context);
        }
    }

    public static class HttpResponseWrapper {
        @HostAccess.Export public final int status;
        @HostAccess.Export public final String body;
        @HostAccess.Export public final Map<String, String> headers;
        @HostAccess.Export public final Map<String, Double> timings;
        private final Context context;

        public HttpResponseWrapper(HttpResponse<String> response, double durationMs, Context context) {
            this.status = response.statusCode();
            this.body = response.body();
            this.context = context;
            
            this.headers = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
                this.headers.put(entry.getKey().toLowerCase(), String.join(",", entry.getValue()));
            }
            
            this.timings = new HashMap<>();
            this.timings.put("duration", durationMs);
        }

        public HttpResponseWrapper(String error, double durationMs, Context context) {
            this.status = 0;
            this.body = error;
            this.context = context;
            this.headers = new HashMap<>();
            this.timings = new HashMap<>();
            this.timings.put("duration", durationMs);
        }

        @HostAccess.Export
        public Object json() {
            Value parse = context.eval("js", "JSON.parse");
            return parse.execute(body);
        }
    }
}
