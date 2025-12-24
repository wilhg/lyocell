package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.engine.ExecutionContext;
import com.wilhg.lyocell.engine.TestEngine;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

public class ExecutionModule implements LyocellModule {
    private TestEngine testEngine;

    @Override
    public String getName() {
        return "lyocell/execution";
    }

    @Override
    public String getJsSource() {
        return """
            const Execution = globalThis.LyocellExecution;
            export const vu = {
                get idInTest() { return Execution.getVuId(); },
                get iterationInInstance() { return Execution.getIteration(); }
            };
            export const test = {
                abort: () => Execution.abort()
            };
            export default { vu, test };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        this.testEngine = moduleContext.testEngine();
        context.getBindings("js").putMember("LyocellExecution", this);
    }

    @HostAccess.Export
    public void abort() {
        if (testEngine != null) {
            testEngine.abort();
        }
    }

    @HostAccess.Export
    public int getVuId() {
        ExecutionContext ctx = ExecutionContext.get();
        return ctx != null ? ctx.vuId() : 0;
    }

    @HostAccess.Export
    public int getIteration() {
        ExecutionContext ctx = ExecutionContext.get();
        return ctx != null ? ctx.iteration() : 0;
    }
}
