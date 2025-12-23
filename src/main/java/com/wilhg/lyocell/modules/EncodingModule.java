package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncodingModule implements LyocellModule {
    @Override
    public String getName() {
        return "k6/encoding";
    }

    @Override
    public String getJsSource() {
        return """
            const Encoding = globalThis.LyocellEncoding;
            export const b64encode = (input) => Encoding.b64encode(input);
            export const b64decode = (input) => Encoding.b64decode(input);
            export default { b64encode, b64decode };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellEncoding", this);
    }

    @HostAccess.Export
    public String b64encode(String input) {
        if (input == null) return null;
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    @HostAccess.Export
    public String b64decode(String input) {
        if (input == null) return null;
        return new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
    }
}
