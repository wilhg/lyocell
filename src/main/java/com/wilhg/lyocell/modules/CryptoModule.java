package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class CryptoModule implements LyocellModule {
    @Override
    public String getName() {
        return "lyocell/crypto";
    }

    @Override
    public String getJsSource() {
        return """
            const Crypto = globalThis.LyocellCrypto;
            export const sha256 = (input, outputEncoding) => Crypto.sha256(input, outputEncoding);
            export const hmac = (algo, secret, data, outputEncoding) => Crypto.hmac(algo, secret, data, outputEncoding);
            export default { sha256, hmac };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellCrypto", this);
    }

    @HostAccess.Export
    public String sha256(String input, String outputEncoding) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return encode(hash, outputEncoding);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @HostAccess.Export
    public String hmac(String algo, String secret, String data, String outputEncoding) {
        try {
            String javaAlgo = switch (algo.toLowerCase()) {
                case "sha256" -> "HmacSHA256";
                case "sha1" -> "HmacSHA1";
                case "md5" -> "HmacMD5";
                default -> throw new IllegalArgumentException("Unsupported hmac algorithm: " + algo);
            };

            Mac mac = Mac.getInstance(javaAlgo);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), javaAlgo);
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return encode(hmac, outputEncoding);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String encode(byte[] data, String encoding) {
        if ("hex".equalsIgnoreCase(encoding) || encoding == null) {
            return HexFormat.of().formatHex(data);
        } else if ("base64".equalsIgnoreCase(encoding)) {
            return java.util.Base64.getEncoder().encodeToString(data);
        }
        throw new IllegalArgumentException("Unsupported output encoding: " + encoding);
    }
}
