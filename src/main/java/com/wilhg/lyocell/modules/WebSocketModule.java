package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class WebSocketModule implements LyocellModule {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private Context context;

    @Override
    public String getName() {
        return "lyocell/ws";
    }

    @Override
    public String getJsSource() {
        return """
            const Ws = globalThis.LyocellWs;
            export const connect = (url, params, callback) => Ws.connect(url, params, callback);
            export default { connect };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.context = context;
        context.getBindings("js").putMember("LyocellWs", this);
    }

    @HostAccess.Export
    public Object connect(String url, Value params, Value callback) {
        CompletableFuture<WebSocketResponse> future = new CompletableFuture<>();
        
        WebSocket.Builder builder = client.newWebSocketBuilder();
        if (params != null && params.hasMember("headers")) {
            Value headers = params.getMember("headers");
            for (String key : headers.getMemberKeys()) {
                builder.header(key, headers.getMember(key).asString());
            }
        }

        builder.buildAsync(URI.create(url), new WebSocket.Listener() {
            private final Map<String, Value> handlers = new HashMap<>();
            private WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket) {
                this.webSocket = webSocket;
                SocketWrapper wrapper = new SocketWrapper(webSocket, handlers);
                if (callback != null && callback.canExecute()) {
                    callback.execute(wrapper);
                }
                if (handlers.containsKey("open")) {
                    handlers.get("open").execute();
                }
                future.complete(new WebSocketResponse(101));
                WebSocket.Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                if (handlers.containsKey("message")) {
                    handlers.get("message").execute(data.toString());
                }
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                if (handlers.containsKey("close")) {
                    handlers.get("close").execute(statusCode, reason);
                }
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                if (handlers.containsKey("error")) {
                    handlers.get("error").execute(error.getMessage());
                }
                future.completeExceptionally(error);
            }
        });

        try {
            return future.get(); // Block virtual thread
        } catch (Exception e) {
            return new WebSocketResponse(0);
        }
    }

    public static class WebSocketResponse {
        @HostAccess.Export public final int status;
        public WebSocketResponse(int status) { this.status = status; }
    }

    public static class SocketWrapper {
        private final WebSocket webSocket;
        private final Map<String, Value> handlers;

        public SocketWrapper(WebSocket webSocket, Map<String, Value> handlers) {
            this.webSocket = webSocket;
            this.handlers = handlers;
        }

        @HostAccess.Export
        public void on(String event, Value handler) {
            handlers.put(event, handler);
        }

        @HostAccess.Export
        public void send(String data) {
            webSocket.sendText(data, true);
        }

        @HostAccess.Export
        public void close() {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        }
    }
}

