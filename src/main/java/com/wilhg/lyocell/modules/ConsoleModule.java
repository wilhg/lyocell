package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ConsoleModule implements LyocellModule {
    @Override
    public String getName() {
        return "lyocell/console";
    }

    @Override
    public String getJsSource() {
        return "export default globalThis.console;";
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("console", this);
    }

    @HostAccess.Export
    public void log(Object... args) {
        String message = Arrays.stream(args)
                .map(String::valueOf)
                .collect(Collectors.joining(" "));
        System.out.println(message);
    }
}
