package com.wilhg.lyocell.modules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilhg.lyocell.engine.JsEngine;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class McpModule implements LyocellModule {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .build();
    private final Map<String, McpClientWrapper> activeClients = new ConcurrentHashMap<>();
    private JsEngine jsEngine;

    @Override
    public String getName() {
        return "lyocell/mcp";
    }

    @Override
    public String getJsSource() {
        return """
            const Mcp = globalThis.LyocellMcp;
            export function connect(url, options) {
                return Mcp.connect(url, options);
            }
            export default { connect };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.jsEngine = moduleContext.jsEngine();
        context.getBindings("js").putMember("LyocellMcp", this);
    }

    @Override
    public void close() {
        activeClients.values().forEach(McpClientWrapper::close);
        activeClients.clear();
    }

    @HostAccess.Export
    public McpClientWrapper connect(String url, Value options) {
        McpClientWrapper client = new McpClientWrapper(url, options, httpClient, jsEngine);
        activeClients.put(url + System.nanoTime(), client);
        client.start();
        return client;
    }

    public static class McpClientWrapper {
        private final String sseUrl;
        private final Map<String, String> headers = new HashMap<>();
        private final HttpClient httpClient;
        private final JsEngine jsEngine;
        private final AtomicLong requestId = new AtomicLong(1);
        private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
        private final Map<String, Value> requestHandlers = new ConcurrentHashMap<>();
        private volatile String postUrl;
        private volatile boolean closed = false;

        public McpClientWrapper(String url, Value options, HttpClient httpClient, JsEngine jsEngine) {
            this.sseUrl = url;
            this.httpClient = httpClient;
            this.jsEngine = jsEngine;
            if (options != null && options.hasMember("headers")) {
                Value headersVal = options.getMember("headers");
                for (String key : headersVal.getMemberKeys()) {
                    headers.put(key, headersVal.getMember(key).asString());
                }
            }
        }

        public void start() {
            Thread.ofVirtual().start(this::listenToSse);
        }

        private void listenToSse() {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sseUrl))
                    .header("Accept", "text/event-stream")
                    .build();

            try {
                HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null && !closed) {
                        if (line.startsWith("data: ")) {
                            handleIncomingMessage(line.substring(6));
                        }
                    }
                }
            } catch (Exception e) {
                if (!closed) {
                    System.err.println("MCP SSE Error: " + e.getMessage());
                }
            }
        }

        private void handleIncomingMessage(String data) {
            try {
                JsonNode node = mapper.readTree(data);
                if (node.has("id")) {
                    long id = node.get("id").asLong();
                    if (node.has("result") || node.has("error")) {
                        CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.complete(node);
                        }
                    } else if (node.has("method")) {
                        handleServerRequest(node);
                    }
                } else if (node.has("method")) {
                    handleNotification(node);
                } else if (node.isTextual()) {
                    this.postUrl = node.asText();
                    if (!this.postUrl.startsWith("http")) {
                        URI sseUri = URI.create(sseUrl);
                        this.postUrl = sseUri.resolve(this.postUrl).toString();
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }

        private void handleServerRequest(JsonNode request) {
            String method = request.get("method").asText();
            jsEngine.executeAsync(() -> {
                Value handler;
                jsEngine.enter();
                try {
                    handler = requestHandlers.get(method);
                } finally {
                    jsEngine.leave();
                }
                
                if (handler != null) {
                    try {
                        Object result = handler.execute(deepExtract(request.get("params")));
                        sendResponse(request.get("id").asLong(), result, null);
                    } catch (Exception e) {
                        sendResponse(request.get("id").asLong(), null, e.getMessage());
                    }
                }
            });
        }

        private void handleNotification(JsonNode notification) {
            // TODO: Support notification handlers
        }

        private void sendResponse(long id, Object result, String error) {
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            if (error != null) {
                Map<String, Object> errorObj = new HashMap<>();
                errorObj.put("code", -32000);
                errorObj.put("message", error);
                response.put("error", errorObj);
            } else {
                response.put("result", result);
            }
            sendAsync(response);
        }

        @HostAccess.Export
        public Object initialize(Value params) {
            return call("initialize", deepExtract(params));
        }

        @HostAccess.Export
        public Object listTools() {
            return call("tools/list", null);
        }

        @HostAccess.Export
        public Object callTool(String name, Value arguments) {
            Map<String, Object> params = new HashMap<>();
            params.put("name", name);
            if (arguments != null) {
                params.put("arguments", deepExtract(arguments));
            }
            return call("tools/call", params);
        }

        @HostAccess.Export
        public void onRequest(String method, Value handler) {
            requestHandlers.put(method, handler);
        }

        @HostAccess.Export
        public void close() {
            closed = true;
        }

        private Object deepExtract(JsonNode node) {
            if (node == null || node.isNull()) return null;
            if (node.isTextual()) return node.asText();
            if (node.isBoolean()) return node.asBoolean();
            if (node.isNumber()) return node.numberValue();
            if (node.isArray()) {
                List<Object> list = new ArrayList<>();
                for (JsonNode item : node) {
                    list.add(deepExtract(item));
                }
                return list;
            }
            if (node.isObject()) {
                Map<String, Object> map = new HashMap<>();
                node.fields().forEachRemaining(entry -> map.put(entry.getKey(), deepExtract(entry.getValue())));
                return map;
            }
            return null;
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
            return val.as(Object.class);
        }

        private Object call(String method, Object params) {
            long id = requestId.getAndIncrement();
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            if (params != null) {
                request.put("params", params);
            }

            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingRequests.put(id, future);

            sendAsync(request);

            try {
                while (!future.isDone()) {
                    int state = jsEngine.pause();
                    try {
                        jsEngine.processEvents();
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } finally {
                        jsEngine.resume(state);
                    }
                }
                JsonNode response = future.get(30, TimeUnit.SECONDS);
                if (response.has("error")) {
                    throw new RuntimeException(response.get("error").get("message").asText());
                }
                return mapper.convertValue(response.get("result"), Object.class);
            } catch (Exception e) {
                throw new RuntimeException("MCP Request failed: " + e.getMessage());
            }
        }

        private void sendAsync(Map<String, Object> message) {
            int retries = 0;
            while (postUrl == null && retries < 100) {
                int state = jsEngine.pause();
                try {
                    jsEngine.processEvents();
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    jsEngine.resume(state);
                }
                retries++;
            }

            if (postUrl == null) {
                throw new RuntimeException("MCP endpoint not established");
            }

            try {
                String body = mapper.writeValueAsString(message);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(postUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                headers.forEach(builder::header);

                httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
