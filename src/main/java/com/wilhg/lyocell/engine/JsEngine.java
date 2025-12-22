package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.js.LyocellFileSystem;
import com.wilhg.lyocell.metrics.MetricsCollector;
import com.wilhg.lyocell.modules.ConsoleModule;
import com.wilhg.lyocell.modules.CoreModule;
import com.wilhg.lyocell.modules.HttpModule;
import com.wilhg.lyocell.modules.MetricsModule;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import java.io.IOException;
import java.nio.file.Path;

public class JsEngine implements AutoCloseable {
    private final Context context;

    public JsEngine() {
        this(java.util.Collections.emptyMap(), new MetricsCollector());
    }

    public JsEngine(java.util.Map<String, Object> extraBindings, MetricsCollector metricsCollector) {
        this.context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowIO(true)
                .fileSystem(new LyocellFileSystem())
                .option("js.esm-eval-returns-exports", "true")
                .build();

        // Inject global bindings
        context.getBindings("js").putMember("LyocellHttp", new HttpModule());
        context.getBindings("js").putMember("LyocellCore", new CoreModule());
        context.getBindings("js").putMember("LyocellMetrics", new MetricsModule(metricsCollector));
        context.getBindings("js").putMember("console", new ConsoleModule());
        
        // Inject extra bindings (e.g., for testing)
        extraBindings.forEach((k, v) -> context.getBindings("js").putMember(k, v));
    }

    private Value moduleExports;

    public void runScript(Path scriptPath) throws IOException {
        Source source = Source.newBuilder("js", scriptPath.toFile())
                .mimeType("application/javascript+module")
                .build();

        // Evaluate the module and keep the exports
        this.moduleExports = context.eval(source);
    }

    public boolean hasExport(String name) {
        return moduleExports != null && moduleExports.hasMember(name);
    }

    public Value executeSetup() {
        if (hasExport("setup")) {
            return moduleExports.getMember("setup").execute();
        }
        return null;
    }

    public void executeDefault(Object data) {
        if (hasExport("default")) {
            Value defaultFn = moduleExports.getMember("default");
            if (data != null) {
                defaultFn.execute(data);
            } else {
                defaultFn.execute();
            }
        }
    }

    public void executeTeardown(Object data) {
        if (hasExport("teardown")) {
            Value teardownFn = moduleExports.getMember("teardown");
            if (data != null) {
                teardownFn.execute(data);
            } else {
                teardownFn.execute();
            }
        }
    }

    // Helper to "clone" data via JSON to ensure isolation between VUs
    public Object parseJsonData(String json) {
        if (json == null) return null;
        return context.eval("js", "JSON.parse('" + json + "')");
    }

    public String toJson(Value value) {
        if (value == null || value.isNull()) return null;
        Value jsonStringify = context.eval("js", "JSON.stringify");
        return jsonStringify.execute(value).asString();
    }


    public void close() {
        context.close();
    }
}
