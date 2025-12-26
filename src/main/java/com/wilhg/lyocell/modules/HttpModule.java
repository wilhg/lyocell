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
                subtasks.add(scope.fork(() -> request(br.method, br.url, br.body, br.params)));
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
        Map<String, Object> params; // Use Map instead of Value to be thread-safe
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
            br.body = reqSize > 2 ? deepExtract(req.getArrayElement(2)) : null;
            if (reqSize > 3) {
                br.params = extractParams(req.getArrayElement(3));
            }
        } else if (req.hasMembers()) {
            br.method = req.hasMember("method") ? req.getMember("method").asString() : "GET";
            br.url = req.hasMember("url") ? req.getMember("url").asString() : "";
            br.body = req.hasMember("body") ? deepExtract(req.getMember("body")) : null;
            if (req.hasMember("params")) {
                br.params = extractParams(req.getMember("params"));
            }
        }
        return br;
    }

    private Map<String, Object> extractParams(Value paramsVal) {
        if (paramsVal == null || paramsVal.isNull()) return null;
        return (Map<String, Object>) deepExtract(paramsVal);
    }

    private Object deepExtract(Value val) {
        if (val == null || val.isNull()) return null;
        if (val.isString()) return val.asString();
        if (val.isBoolean()) return val.asBoolean();
        if (val.isNumber()) {
            if (val.fitsInInt()) return val.asInt();
            if (val.fitsInLong()) return val.asLong();
            return val.asDouble();
        }
        if (val.hasArrayElements()) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < val.getArraySize(); i++) {
                list.add(deepExtract(val.getArrayElement(i)));
            }
            return list;
        }
        if (val.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : val.getMemberKeys()) {
                map.put(key, deepExtract(val.getMember(key)));
            }
            return map;
        }
        if (val.isHostObject()) {
            return val.asHostObject();
        }
        return val.toString();
    }

    @HostAccess.Export
    public HttpResponseWrapper get(String url, Value params) {
        return request("GET", url, null, params != null ? extractParams(params) : null);
    }

    @HostAccess.Export
    public HttpResponseWrapper post(String url, Object body, Value params) {
        return request("POST", url, body, params != null ? extractParams(params) : null);
    }

    @HostAccess.Export
    public HttpResponseWrapper put(String url, Object body, Value params) {
        return request("PUT", url, body, params != null ? extractParams(params) : null);
    }

    @HostAccess.Export
    public HttpResponseWrapper patch(String url, Object body, Value params) {
        return request("PATCH", url, body, params != null ? extractParams(params) : null);
    }

    @HostAccess.Export
    public HttpResponseWrapper del(String url, Object body, Value params) {
        return request("DELETE", url, body, params != null ? extractParams(params) : null);
    }

    private HttpResponseWrapper request(String method, String url, Object body, Map<String, Object> params) {
        Instant start = Instant.now();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url));

            boolean insecure = false;
            boolean followRedirects = true;

            if (params != null) {
                // Set timeout
                if (params.containsKey("timeout")) {
                    Object timeoutVal = params.get("timeout");
                    Duration timeout = parseDuration(timeoutVal);
                    if (!timeout.isZero()) {
                        builder.timeout(timeout);
                    }
                }

                // Set headers
                if (params.get("headers") instanceof Map<?, ?> headers) {
                    headers.forEach((k, v) -> builder.header(k.toString(), v.toString()));
                }

                // Insecure TLS
                if (params.get("insecureSkipTLSVerify") instanceof Boolean b) {
                    insecure = b;
                }

                // Redirects
                if (params.get("redirects") instanceof Number n) {
                    followRedirects = n.intValue() > 0;
                }

                // Auth
                if (params.get("auth") != null) {
                    Object auth = params.get("auth");
                    String authHeader = null;
                    if (auth instanceof String s) {
                        authHeader = "Basic " + Base64.getEncoder().encodeToString(s.getBytes());
                    } else if (auth instanceof Map<?, ?> m) {
                        Object user = m.get("username");
                        Object pass = m.get("password");
                        if (user != null && pass != null) {
                            String userPass = user.toString() + ":" + pass.toString();
                            authHeader = "Basic " + Base64.getEncoder().encodeToString(userPass.getBytes());
                        }
                    }
                    if (authHeader != null) {
                        builder.header("Authorization", authHeader);
                    }
                }
            }

            // Extract tags
            Map<String, String> tags = new HashMap<>();
            if (params != null && params.get("tags") instanceof Map<?, ?> tagsMap) {
                tagsMap.forEach((k, v) -> tags.put(k.toString(), v.toString()));
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

    private Duration parseDuration(Object value) {
        if (value instanceof Number n) {
            return Duration.ofMillis(n.longValue());
        }
        if (value instanceof String s) {
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
