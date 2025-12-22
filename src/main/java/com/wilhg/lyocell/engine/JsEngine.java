package com.wilhg.lyocell.engine;

import com.wilhg.lyocell.js.LyocellFileSystem;
import com.wilhg.lyocell.modules.ConsoleModule;
import com.wilhg.lyocell.modules.CoreModule;
import com.wilhg.lyocell.modules.HttpModule;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import java.io.IOException;
import java.nio.file.Path;

public class JsEngine implements AutoCloseable {
    private final Context context;

    public JsEngine() {
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
        context.getBindings("js").putMember("console", new ConsoleModule());
    }

    public void runScript(Path scriptPath) throws IOException {
        Source source = Source.newBuilder("js", scriptPath.toFile())
                .mimeType("application/javascript+module")
                .build();

        context.eval(source);
    }

    public void close() {
        context.close();
    }
}
