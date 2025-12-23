package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.js.LyocellFileSystem;
import com.wilhg.lyocell.metrics.MetricsCollector;
import com.wilhg.lyocell.modules.LyocellModule;
import com.wilhg.lyocell.modules.ModuleContext;
import com.wilhg.lyocell.modules.ModuleRegistry;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class JsEngine implements AutoCloseable {
    private final Context context;
    private final TestEngine testEngine;

    public JsEngine() {
        this(java.util.Collections.emptyMap(), new MetricsCollector(), new TestEngine());
    }

    public JsEngine(Map<String, Object> extraBindings, MetricsCollector metricsCollector) {
        this(extraBindings, metricsCollector, new TestEngine());
    }

    public JsEngine(Map<String, Object> extraBindings, MetricsCollector metricsCollector, TestEngine testEngine) {
        this(extraBindings, metricsCollector, ModuleRegistry.getAllModules(metricsCollector), testEngine);
    }

    public JsEngine(Map<String, Object> extraBindings, MetricsCollector metricsCollector, List<LyocellModule> modules, TestEngine testEngine) {
        this.testEngine = testEngine;
        this.context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowIO(IOAccess.newBuilder()
                        .fileSystem(new LyocellFileSystem(metricsCollector))
                        .build())
                .allowExperimentalOptions(true)
                .option("js.esm-eval-returns-exports", "true")
                .option("engine.WarnVirtualThreadSupport", "false")
                .build();

        // Install modules
        ModuleContext moduleContext = new ModuleContext(metricsCollector, testEngine);
        for (LyocellModule module : modules) {
            module.install(this.context, moduleContext);
        }
        
        // Setup __ENV
        java.util.Map<String, String> env = new java.util.HashMap<>(System.getenv());
        if (extraBindings.containsKey("__ENV")) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> extras = (java.util.Map<String, String>) extraBindings.get("__ENV");
            env.putAll(extras);
        }
        
        context.getBindings("js").putMember("__ENV", env);
        
        // Inject extra bindings (e.g., for testing)
        extraBindings.forEach((k, v) -> {
            if (!k.equals("__ENV")) {
                context.getBindings("js").putMember(k, v);
            }
        });
    }

    private Value moduleExports;

    public void runScript(Path scriptPath) throws IOException {
        Source source = Source.newBuilder("js", scriptPath.toFile())
                .mimeType("application/javascript+module")
                .build();

        // Evaluate the module and keep the exports
        this.moduleExports = context.eval(source);
    }

    public Value getOptions() {
        if (hasExport("options")) {
            return moduleExports.getMember("options");
        }
        return null;
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
        executeFunction("default", data);
    }

    public void executeFunction(String name, Object data) {
        if (hasExport(name)) {
            Value fn = moduleExports.getMember(name);
            if (data != null) {
                fn.execute(data);
            } else {
                fn.execute();
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
