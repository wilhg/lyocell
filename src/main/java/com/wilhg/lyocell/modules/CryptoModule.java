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
            export const sha256 = (input, outputEncoding) => Crypto.hash("sha256", input, outputEncoding);
            export const sha1 = (input, outputEncoding) => Crypto.hash("sha1", input, outputEncoding);
            export const md5 = (input, outputEncoding) => Crypto.hash("md5", input, outputEncoding);
            export const hmac = (algo, secret, data, outputEncoding) => Crypto.hmac(algo, secret, data, outputEncoding);
            export const createHash = (algo) => {
                return {
                    update: (data) => Crypto.updateHash(algo, data),
                    digest: (encoding) => Crypto.digestHash(algo, encoding)
                };
            };
            export default { sha256, sha1, md5, hmac, createHash };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellCrypto", this);
    }

    // Temporary storage for incremental hashing (simplistic implementation for MVP)
    private final java.util.Map<String, MessageDigest> activeDigests = new java.util.concurrent.ConcurrentHashMap<>();

    @HostAccess.Export
    public void updateHash(String algo, String data) {
        try {
            MessageDigest digest = activeDigests.computeIfAbsent(algo, a -> {
                try {
                    return MessageDigest.getInstance(normalizeAlgo(a));
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            });
            digest.update(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @HostAccess.Export
    public String digestHash(String algo, String outputEncoding) {
        MessageDigest digest = activeDigests.remove(algo);
        if (digest == null) return "";
        return encode(digest.digest(), outputEncoding);
    }

    @HostAccess.Export
    public String hash(String algo, String input, String outputEncoding) {
        try {
            MessageDigest digest = MessageDigest.getInstance(normalizeAlgo(algo));
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return encode(hash, outputEncoding);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String normalizeAlgo(String algo) {
        return switch (algo.toLowerCase()) {
            case "sha256" -> "SHA-256";
            case "sha1" -> "SHA-1";
            case "md5" -> "MD5";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + algo);
        };
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
