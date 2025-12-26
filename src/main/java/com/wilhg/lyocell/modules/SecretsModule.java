package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

import java.util.Map;

public class SecretsModule implements LyocellModule {
    @Override
    public String getName() {
        return "lyocell/secrets";
    }

    @Override
    public String getJsSource() {
        return """
            const Secrets = globalThis.LyocellSecrets;
            export const get = (key) => Secrets.get(key);
            export default { get };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellSecrets", this);
    }

    @HostAccess.Export
    public String get(String key) {
        // In a real implementation, this would look up from a secure store.
        // For now, we use environment variables.
        return System.getenv(key);
    }
}

