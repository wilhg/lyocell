package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpModule {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    
    private Context context;

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

            return new HttpResponseWrapper(response, context);
        } catch (Exception e) {
            return new HttpResponseWrapper(e.getMessage(), context);
        }
    }

    public static class HttpResponseWrapper {
        @HostAccess.Export public final int status;
        @HostAccess.Export public final String body;
        private final Context context;

        public HttpResponseWrapper(HttpResponse<String> response, Context context) {
            this.status = response.statusCode();
            this.body = response.body();
            this.context = context;
        }

        public HttpResponseWrapper(String error, Context context) {
            this.status = 0;
            this.body = error;
            this.context = context;
        }

        @HostAccess.Export
        public Object json() {
            Value parse = context.eval("js", "JSON.parse");
            return parse.execute(body);
        }
    }
}
