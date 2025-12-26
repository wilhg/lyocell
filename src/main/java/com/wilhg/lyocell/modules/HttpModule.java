package com.wilhg.lyocell.modules;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.wilhg.lyocell.metrics.MetricsCollector;

public class HttpModule implements LyocellModule {
    private final CookieManager cookieManager = new CookieManager();
    private final Map<String, HttpClient> clients = new ConcurrentHashMap<>();
    
    private Context context;
    private MetricsCollector metricsCollector;

    public HttpModule() {
        // Default client
        clients.put("default", HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(cookieManager)
                .build());
    }

    public HttpModule(MetricsCollector metricsCollector) {
        this();
        this.metricsCollector = metricsCollector;
    }

    private HttpClient getClient(boolean insecure, boolean followRedirects) {
        String key = "insecure=" + insecure + "&redirects=" + followRedirects;
        return clients.computeIfAbsent(key, k -> {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .cookieHandler(cookieManager);

            if (followRedirects) {
                builder.followRedirects(HttpClient.Redirect.NORMAL);
            } else {
                builder.followRedirects(HttpClient.Redirect.NEVER);
            }

            if (insecure) {
                try {
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }}, new java.security.SecureRandom());
                    builder.sslContext(sslContext);
                } catch (Exception e) {
                    // Fallback to default
                }
            }
            return builder.build();
        });
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
            export const batch = (requests) => Http.batch(requests);
            export const cookieJar = () => Http.cookieJar();
            export function CookieJar() { return Http.newCookieJar(); }
            export default { get, post, put, patch, del, batch, cookieJar, CookieJar };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.context = context;
        this.metricsCollector = moduleContext.metricsCollector();
        context.getBindings("js").putMember("LyocellHttp", this);
    }

    @HostAccess.Export
    public CookieJarWrapper cookieJar() {
        return new CookieJarWrapper(cookieManager);
    }

    @HostAccess.Export
    public CookieJarWrapper newCookieJar() {
        return new CookieJarWrapper(new CookieManager());
    }

    public static class CookieJarWrapper {
        private final CookieManager manager;

        public CookieJarWrapper(CookieManager manager) {
            this.manager = manager;
        }

        @HostAccess.Export
        public void set(String url, String name, String value, Object options) {
            java.net.HttpCookie cookie = new java.net.HttpCookie(name, value);
            // Apply options (domain, path, etc.) if needed
            manager.getCookieStore().add(URI.create(url), cookie);
        }

        @HostAccess.Export
        public Map<String, List<String>> cookiesForURL(String url) {
            try {
                return manager.get(URI.create(url), new HashMap<>());
            } catch (Exception e) {
                return new HashMap<>();
            }
        }
    }

    @HostAccess.Export
    public Object[] batch(Value requests) {
        if (!requests.hasArrayElements()) {
            return new Object[0];
        }

        long size = requests.getArraySize();
        Object[] results = new Object[(int) size];
        List<BatchRequest> batchRequests = new ArrayList<>();

        // Extract all data on the main thread first to avoid GraalVM thread-safety issues
        for (int i = 0; i < size; i++) {
            Value req = requests.getArrayElement(i);
            batchRequests.add(extractBatchRequest(req));
        }

        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
            List<java.util.concurrent.StructuredTaskScope.Subtask<HttpResponseWrapper>> subtasks = new ArrayList<>();

            for (BatchRequest br : batchRequests) {
                subtasks.add(scope.fork(() -> request(br.method, br.url, br.body, null))); // Note: simplified params for now
            }

            scope.join();

            for (int i = 0; i < size; i++) {
                results[i] = subtasks.get(i).get();
            }
        } catch (Exception e) {
            for (int i = 0; i < size; i++) {
                if (results[i] == null) {
                    results[i] = new HttpResponseWrapper(e.getMessage(), 0, context);
                }
            }
        }

        return results;
    }

    private static class BatchRequest {
        String method;
        String url;
        Object body;
    }

    private BatchRequest extractBatchRequest(Value req) {
        BatchRequest br = new BatchRequest();
        if (req.isString()) {
            br.method = "GET";
            br.url = req.asString();
        } else if (req.hasArrayElements()) {
            long reqSize = req.getArraySize();
            br.method = reqSize > 0 ? req.getArrayElement(0).asString() : "GET";
            br.url = reqSize > 1 ? req.getArrayElement(1).asString() : "";
            br.body = reqSize > 2 ? req.getArrayElement(2).asHostObject() : null;
        } else if (req.hasMembers()) {
            br.method = req.hasMember("method") ? req.getMember("method").asString() : "GET";
            br.url = req.hasMember("url") ? req.getMember("url").asString() : "";
            br.body = req.hasMember("body") ? req.getMember("body").asHostObject() : null;
        }
        return br;
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

            boolean insecure = false;
            boolean followRedirects = true;

            if (params != null) {
                // Set timeout
                if (params.hasMember("timeout")) {
                    Value timeoutVal = params.getMember("timeout");
                    Duration timeout = parseDuration(timeoutVal);
                    if (!timeout.isZero()) {
                        builder.timeout(timeout);
                    }
                }

                // Set headers
                if (params.hasMember("headers")) {
                    Value headers = params.getMember("headers");
                    for (String key : headers.getMemberKeys()) {
                        builder.header(key, headers.getMember(key).asString());
                    }
                }

                // Insecure TLS
                if (params.hasMember("insecureSkipTLSVerify")) {
                    insecure = params.getMember("insecureSkipTLSVerify").asBoolean();
                }

                // Redirects
                if (params.hasMember("redirects")) {
                    followRedirects = params.getMember("redirects").asInt() > 0;
                }

                // Auth
                if (params.hasMember("auth")) {
                    Value auth = params.getMember("auth");
                    String authHeader = null;
                    if (auth.isString()) {
                        authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.asString().getBytes());
                    } else if (auth.hasMember("username") && auth.hasMember("password")) {
                        String userPass = auth.getMember("username").asString() + ":" + auth.getMember("password").asString();
                        authHeader = "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes());
                    }
                    if (authHeader != null) {
                        builder.header("Authorization", authHeader);
                    }
                }
            }

            // Extract tags
            Map<String, String> tags = new HashMap<>();
            if (params != null && params.hasMember("tags")) {
                Value tagsVal = params.getMember("tags");
                for (String key : tagsVal.getMemberKeys()) {
                    tags.put(key, tagsVal.getMember(key).asString());
                }
            }

            // Set method and body
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
            if (body != null) {
                bodyPublisher = HttpRequest.BodyPublishers.ofString(body.toString());
            }
            builder.method(method, bodyPublisher);

            HttpClient client = getClient(insecure, followRedirects);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            double duration = Duration.between(start, Instant.now()).toMillis();
            
            if (metricsCollector != null) {
                metricsCollector.addTrend("http_req_duration", duration, tags);
                metricsCollector.addCounter("http_reqs", 1, tags);
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
        @HostAccess.Export public final String proto;
        @HostAccess.Export public final Map<String, Object> tls_info;
        @HostAccess.Export public final Map<String, Object> ocsp;
        private final Context context;

        public HttpResponseWrapper(HttpResponse<String> response, double durationMs, Context context) {
            this.status = response.statusCode();
            this.body = response.body();
            this.context = context;
            this.proto = response.version().name();
            
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

            // TLS Info (simplified)
            this.tls_info = new HashMap<>();
            response.sslSession().ifPresent(session -> {
                tls_info.put("version", session.getProtocol());
                tls_info.put("cipher_suite", session.getCipherSuite());
            });

            this.ocsp = new HashMap<>();
            this.ocsp.put("status", "unknown");
        }

        public HttpResponseWrapper(String error, double durationMs, Context context) {
            this.status = 0;
            this.body = error;
            this.context = context;
            this.proto = "";
            this.headers = new HashMap<>();
            this.timings = new HashMap<>();
            this.timings.put("duration", durationMs);
            this.timings.put("blocked", 0.0);
            this.timings.put("connecting", 0.0);
            this.timings.put("tls_handshaking", 0.0);
            this.timings.put("sending", 0.0);
            this.timings.put("waiting", 0.0);
            this.timings.put("receiving", 0.0);
            this.tls_info = new HashMap<>();
            this.ocsp = new HashMap<>();
        }

        @HostAccess.Export
        public Object json() {
            Value parse = context.eval("js", "JSON.parse");
            return parse.execute(body);
        }

        @HostAccess.Export
        public Object html(Value selector) {
            Document doc = Jsoup.parse(body);
            if (selector != null && selector.isString()) {
                return new Selection(doc.select(selector.asString()), context);
            }
            return new Selection(doc, context);
        }
    }

    public static class Selection {
        private final Elements elements;
        private final Context context;

        public Selection(Document doc, Context context) {
            this.elements = new Elements(doc);
            this.context = context;
        }

        public Selection(Elements elements, Context context) {
            this.elements = elements;
            this.context = context;
        }

        @HostAccess.Export
        public String text() {
            return elements.text();
        }

        @HostAccess.Export
        public String attr(String name) {
            return elements.attr(name);
        }

        @HostAccess.Export
        public Selection find(String selector) {
            return new Selection(elements.select(selector), context);
        }

        @HostAccess.Export
        public int size() {
            return elements.size();
        }

        @HostAccess.Export
        public Object get(int index) {
            if (index >= 0 && index < elements.size()) {
                return new Selection(new Elements(elements.get(index)), context);
            }
            return null;
        }

        @HostAccess.Export
        public Object first() {
            return get(0);
        }

        @HostAccess.Export
        public Object last() {
            return get(elements.size() - 1);
        }

        @HostAccess.Export
        public Object[] toArray() {
            Object[] result = new Object[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                result[i] = new Selection(new Elements(elements.get(i)), context);
            }
            return result;
        }
    }
}
