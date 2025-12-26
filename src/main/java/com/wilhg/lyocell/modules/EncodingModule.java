package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncodingModule implements LyocellModule {
    @Override
    public String getName() {
        return "lyocell/encoding";
    }

    @Override
    public String getJsSource() {
        return """
            const Encoding = globalThis.LyocellEncoding;
            export const b64encode = (input, variant) => Encoding.b64encode(input, variant);
            export const b64decode = (input, variant) => Encoding.b64decode(input, variant);
            export default { b64encode, b64decode };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellEncoding", this);
    }

    @HostAccess.Export
    public String b64encode(String input, String variant) {
        if (input == null) return null;
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        return encode(bytes, variant);
    }

    @HostAccess.Export
    public String b64decode(String input, String variant) {
        if (input == null) return null;
        byte[] bytes = decode(input, variant);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String encode(byte[] data, String variant) {
        Base64.Encoder encoder = switch (normalizeVariant(variant)) {
            case "std" -> Base64.getEncoder();
            case "rawstd" -> Base64.getEncoder().withoutPadding();
            case "url" -> Base64.getUrlEncoder();
            case "rawurl" -> Base64.getUrlEncoder().withoutPadding();
            default -> Base64.getEncoder();
        };
        return encoder.encodeToString(data);
    }

    private byte[] decode(String input, String variant) {
        Base64.Decoder decoder = switch (normalizeVariant(variant)) {
            case "std", "rawstd" -> Base64.getDecoder();
            case "url", "rawurl" -> Base64.getUrlDecoder();
            default -> Base64.getDecoder();
        };
        return decoder.decode(input);
    }

    private String normalizeVariant(String variant) {
        if (variant == null) return "std";
        return variant.toLowerCase();
    }
}
