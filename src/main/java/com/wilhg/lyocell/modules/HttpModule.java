package com.wilhg.lyocell.modules;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import com.wilhg.lyocell.metrics.MetricsCollector;

public class HttpModule implements LyocellModule {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    
    private Context context;
    private MetricsCollector metricsCollector;

    public HttpModule() {
    }

    public HttpModule(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @Override
    public String getName() {
        return "lyocell/http";
    }

    @Override
    public String getJsSource() {
        return """
            const Http = globalThis.LyocellHttp;
            export const get = (url, params) => Http.get(url, params);
            export const post = (url, body, params) => Http.post(url, body, params);
            export const put = (url, body, params) => Http.put(url, body, params);
            export const patch = (url, body, params) => Http.patch(url, body, params);
            export const del = (url, body, params) => Http.del(url, body, params);
            export default { get, post, put, patch, del };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.context = context;
        this.metricsCollector = moduleContext.metricsCollector();
        context.getBindings("js").putMember("LyocellHttp", this);
    }

    @HostAccess.Export
    public HttpResponseWrapper get(String url, Value params) {
        return request("GET", url, null, params);
    }

    @HostAccess.Export
    public HttpResponseWrapper post(String url, Object body, Value params) {
        return request("POST", url, body, params);
    }

    @HostAccess.Export
    public HttpResponseWrapper put(String url, Object body, Value params) {
        return request("PUT", url, body, params);
    }

    @HostAccess.Export
    public HttpResponseWrapper patch(String url, Object body, Value params) {
        return request("PATCH", url, body, params);
    }

    @HostAccess.Export
    public HttpResponseWrapper del(String url, Object body, Value params) {
        return request("DELETE", url, body, params);
    }

    private HttpResponseWrapper request(String method, String url, Object body, Value params) {
        Instant start = Instant.now();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url));

            // Set timeout
            if (params != null && params.hasMember("timeout")) {
                Value timeoutVal = params.getMember("timeout");
                Duration timeout = parseDuration(timeoutVal);
                if (!timeout.isZero()) {
                    builder.timeout(timeout);
                }
            }

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

    private Duration parseDuration(Value value) {
        if (value.isNumber()) {
            return Duration.ofMillis(value.asLong());
        }
        if (value.isString()) {
            String s = value.asString();
            if (s.endsWith("ms")) return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
            if (s.endsWith("s")) return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
            try {
                return Duration.ofMillis(Long.parseLong(s));
            } catch (NumberFormatException e) {
                return Duration.ZERO;
            }
        }
        return Duration.ZERO;
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
            this.timings.put("blocked", 0.0);
            this.timings.put("connecting", 0.0);
            this.timings.put("tls_handshaking", 0.0);
            this.timings.put("sending", 0.0);
            this.timings.put("waiting", durationMs * 0.8); // Dummy waiting time
            this.timings.put("receiving", durationMs * 0.2); // Dummy receiving time
        }

        public HttpResponseWrapper(String error, double durationMs, Context context) {
            this.status = 0;
            this.body = error;
            this.context = context;
            this.headers = new HashMap<>();
            this.timings = new HashMap<>();
            this.timings.put("duration", durationMs);
            this.timings.put("blocked", 0.0);
            this.timings.put("connecting", 0.0);
            this.timings.put("tls_handshaking", 0.0);
            this.timings.put("sending", 0.0);
            this.timings.put("waiting", 0.0);
            this.timings.put("receiving", 0.0);
        }

        @HostAccess.Export
        public Object json() {
            Value parse = context.eval("js", "JSON.parse");
            return parse.execute(body);
        }
    }
}
