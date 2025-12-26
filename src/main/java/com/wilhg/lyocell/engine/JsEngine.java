package com.wilhg.lyocell.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

import com.wilhg.lyocell.js.LyocellFileSystem;
import com.wilhg.lyocell.metrics.MetricsCollector;
import com.wilhg.lyocell.modules.LyocellModule;
import com.wilhg.lyocell.modules.ModuleContext;
import com.wilhg.lyocell.modules.ModuleRegistry;

public class JsEngine implements AutoCloseable {
    private final Context context;
    private final TestEngine testEngine;
    private final List<LyocellModule> installedModules;
    private final ReentrantLock lock = new ReentrantLock();
    private final BlockingQueue<Runnable> eventQueue = new LinkedBlockingQueue<>();
    private final ThreadLocal<Integer> enterDepth = ThreadLocal.withInitial(() -> 0);

    public JsEngine(MetricsCollector metricsCollector, TestEngine testEngine) {
        this(java.util.Collections.emptyMap(), metricsCollector, testEngine);
    }

    public JsEngine(Map<String, Object> extraBindings, MetricsCollector metricsCollector, TestEngine testEngine) {
        this(extraBindings, metricsCollector, ModuleRegistry.getAllModules(metricsCollector), testEngine);
    }

    public JsEngine(Map<String, Object> extraBindings, MetricsCollector metricsCollector, List<LyocellModule> modules, TestEngine testEngine) {
        this.testEngine = testEngine;
        this.installedModules = modules;
        this.context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(s -> true)
                .allowIO(IOAccess.newBuilder()
                        .fileSystem(new LyocellFileSystem(metricsCollector))
                        .build())
                .allowExperimentalOptions(true)
                .allowCreateThread(true)
                .option("js.esm-eval-returns-exports", "true")
                .option("engine.WarnVirtualThreadSupport", "false")
                .build();

        // Install modules
        ModuleContext moduleContext = new ModuleContext(metricsCollector, testEngine, this);
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
        
        enter();
        try {
            context.getBindings("js").putMember("__ENV", env);
            
            // Inject extra bindings
            extraBindings.forEach((k, v) -> {
                if (!k.equals("__ENV")) {
                    context.getBindings("js").putMember(k, v);
                }
            });
        } finally {
            leave();
        }
    }

    public void enter() {
        lock.lock();
        try {
            context.enter();
            enterDepth.set(enterDepth.get() + 1);
        } catch (Throwable t) {
            lock.unlock();
            throw t;
        }
    }

    public void leave() {
        int depth = enterDepth.get();
        if (depth > 0) {
            context.leave();
            enterDepth.set(depth - 1);
        }
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public int pause() {
        int depth = enterDepth.get();
        for (int i = 0; i < depth; i++) {
            context.leave();
        }
        enterDepth.set(0);
        
        int holdCount = lock.getHoldCount();
        for (int i = 0; i < holdCount; i++) {
            lock.unlock();
        }
        return depth * 1000 + holdCount;
    }

    public void resume(int state) {
        int depth = state / 1000;
        int holdCount = state % 1000;
        for (int i = 0; i < holdCount; i++) {
            lock.lock();
        }
        for (int i = 0; i < depth; i++) {
            context.enter();
        }
        enterDepth.set(depth);
    }

    public void executeAsync(Runnable runnable) {
        eventQueue.add(runnable);
    }

    public void processEvents() {
        Runnable runnable;
        while ((runnable = eventQueue.poll()) != null) {
            enter();
            try {
                runnable.run();
            } catch (Exception e) {
                System.err.println("Async Task Error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                leave();
            }
        }
    }

    public void sleep(double seconds) {
        long end = System.currentTimeMillis() + (long)(seconds * 1000);
        while (System.currentTimeMillis() < end) {
            int state = pause();
            try {
                processEvents();
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                resume(state);
            }
        }
    }

    private Value moduleExports;

    public void runScript(Path scriptPath) throws IOException {
        Source source = Source.newBuilder("js", scriptPath.toFile())
                .mimeType("application/javascript+module")
                .build();
        enter();
        try {
            this.moduleExports = context.eval(source);
        } finally {
            leave();
        }
    }

    public Value getOptions() {
        enter();
        try {
            if (moduleExports != null && moduleExports.hasMember("options")) {
                return moduleExports.getMember("options");
            }
            return null;
        } finally {
            leave();
        }
    }

    public boolean hasExport(String name) {
        enter();
        try {
            return moduleExports != null && moduleExports.hasMember(name);
        } finally {
            leave();
        }
    }

    public Value executeSetup() {
        enter();
        try {
            if (moduleExports != null && moduleExports.hasMember("setup")) {
                return moduleExports.getMember("setup").execute();
            }
            return null;
        } finally {
            leave();
        }
    }

    public void executeDefault(Object data) {
        executeFunction("default", data);
    }

    public void executeFunction(String name, Object data) {
        enter();
        try {
            if (moduleExports != null && moduleExports.hasMember(name)) {
                Value fn = moduleExports.getMember(name);
                if (data != null) {
                    fn.execute(data);
                } else {
                    fn.execute();
                }
                processEvents(); 
            }
        } finally {
            leave();
        }
    }

    public void executeTeardown(Object data) {
        enter();
        try {
            if (moduleExports != null && moduleExports.hasMember("teardown")) {
                Value teardownFn = moduleExports.getMember("teardown");
                if (data != null) {
                    teardownFn.execute(data);
                } else {
                    teardownFn.execute();
                }
                processEvents();
            }
        } finally {
            leave();
        }
    }

    public Object parseJsonData(String json) {
        if (json == null) return null;
        enter();
        try {
            Value jsonParse = context.eval("js", "JSON.parse");
            return jsonParse.execute(json);
        } finally {
            leave();
        }
    }

    public String toJson(Value value) {
        if (value == null || value.isNull()) return null;
        enter();
        try {
            Value jsonStringify = context.eval("js", "JSON.stringify");
            return jsonStringify.execute(value).asString();
        } finally {
            leave();
        }
    }

    public Value eval(String js) {
        enter();
        try {
            return context.eval("js", js);
        } finally {
            leave();
        }
    }

    public Value eval(Source source) {
        enter();
        try {
            return context.eval(source);
        } finally {
            leave();
        }
    }

    public void close() {
        for (LyocellModule module : installedModules) {
            try {
                module.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        context.close();
    }
}
