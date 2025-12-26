package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
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
            export const sha384 = (input, outputEncoding) => Crypto.hash("sha384", input, outputEncoding);
            export const sha512 = (input, outputEncoding) => Crypto.hash("sha512", input, outputEncoding);
            export const hmac = (algo, secret, data, outputEncoding) => Crypto.hmac(algo, secret, data, outputEncoding);
            export const createHash = (algo) => {
                const id = Crypto.createHash(algo);
                return {
                    update: (data) => Crypto.updateHash(id, data),
                    digest: (encoding) => Crypto.digestHash(id, encoding)
                };
            };
            export const createHMAC = (algo, secret) => {
                const id = Crypto.createHMAC(algo, secret);
                return {
                    update: (data) => Crypto.updateHMAC(id, data),
                    digest: (encoding) => Crypto.digestHMAC(id, encoding)
                };
            };
            export const getRandomValues = (typedArray) => Crypto.getRandomValues(typedArray);
            export const randomUUID = () => Crypto.randomUUID();
            export const subtle = {
                digest: (algo, data) => globalThis.LyocellSubtleCrypto.digest(algo, data),
                generateKey: (algo, extractable, usages) => globalThis.LyocellSubtleCrypto.generateKey(algo, extractable, usages),
                encrypt: (algo, key, data) => globalThis.LyocellSubtleCrypto.encrypt(algo, key, data),
                decrypt: (algo, key, data) => globalThis.LyocellSubtleCrypto.decrypt(algo, key, data),
                importKey: (format, keyData, algo, extractable, usages) => globalThis.LyocellSubtleCrypto.importKey(format, keyData, algo, extractable, usages)
            };
            export default { sha256, sha1, md5, sha384, sha512, hmac, createHash, createHMAC, getRandomValues, randomUUID, subtle };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellCrypto", this);
        new SubtleCryptoModule().install(context, moduleContext);
    }

    @HostAccess.Export
    public void getRandomValues(Value typedArray) {
        if (!typedArray.hasArrayElements()) return;
        byte[] bytes = new byte[(int) typedArray.getArraySize()];
        new java.security.SecureRandom().nextBytes(bytes);
        for (int i = 0; i < bytes.length; i++) {
            typedArray.setArrayElement(i, bytes[i]);
        }
    }

    @HostAccess.Export
    public String randomUUID() {
        return java.util.UUID.randomUUID().toString();
    }

    // Storage for incremental hashing and HMAC
    private final java.util.Map<String, MessageDigest> activeDigests = new java.util.HashMap<>();
    private final java.util.Map<String, Mac> activeMacs = new java.util.HashMap<>();
    private final java.util.concurrent.atomic.AtomicLong idCounter = new java.util.concurrent.atomic.AtomicLong();

    @HostAccess.Export
    public String createHash(String algo) {
        try {
            String id = "hash_" + idCounter.incrementAndGet();
            activeDigests.put(id, MessageDigest.getInstance(normalizeAlgo(algo)));
            return id;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @HostAccess.Export
    public void updateHash(String id, String data) {
        MessageDigest digest = activeDigests.get(id);
        if (digest != null) {
            digest.update(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    @HostAccess.Export
    public String digestHash(String id, String outputEncoding) {
        MessageDigest digest = activeDigests.remove(id);
        if (digest == null) return "";
        return encode(digest.digest(), outputEncoding);
    }

    @HostAccess.Export
    public String createHMAC(String algo, String secret) {
        try {
            String id = "hmac_" + idCounter.incrementAndGet();
            String javaAlgo = normalizeHmacAlgo(algo);
            Mac mac = Mac.getInstance(javaAlgo);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), javaAlgo);
            mac.init(secretKey);
            activeMacs.put(id, mac);
            return id;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @HostAccess.Export
    public void updateHMAC(String id, String data) {
        Mac mac = activeMacs.get(id);
        if (mac != null) {
            mac.update(data.getBytes(StandardCharsets.UTF_8));
        }
    }

    @HostAccess.Export
    public String digestHMAC(String id, String outputEncoding) {
        Mac mac = activeMacs.remove(id);
        if (mac == null) return "";
        return encode(mac.doFinal(), outputEncoding);
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
            case "sha384" -> "SHA-384";
            case "sha512" -> "SHA-512";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + algo);
        };
    }

    @HostAccess.Export
    public String hmac(String algo, String secret, String data, String outputEncoding) {
        try {
            String javaAlgo = normalizeHmacAlgo(algo);

            Mac mac = Mac.getInstance(javaAlgo);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), javaAlgo);
            mac.init(secretKey);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return encode(hmac, outputEncoding);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String normalizeHmacAlgo(String algo) {
        return switch (algo.toLowerCase()) {
            case "sha256" -> "HmacSHA256";
            case "sha1" -> "HmacSHA1";
            case "md5" -> "HmacMD5";
            case "sha384" -> "HmacSHA384";
            case "sha512" -> "HmacSHA512";
            default -> throw new IllegalArgumentException("Unsupported hmac algorithm: " + algo);
        };
    }

    private String encode(byte[] data, String encoding) {
        if ("hex".equalsIgnoreCase(encoding) || encoding == null) {
            return HexFormat.of().formatHex(data);
        } else if ("base64".equalsIgnoreCase(encoding)) {
            return java.util.Base64.getEncoder().encodeToString(data);
        } else if ("base64url".equalsIgnoreCase(encoding)) {
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(data);
        } else if ("raw".equalsIgnoreCase(encoding)) {
            return new String(data, StandardCharsets.ISO_8859_1);
        }
        throw new IllegalArgumentException("Unsupported output encoding: " + encoding);
    }
}
