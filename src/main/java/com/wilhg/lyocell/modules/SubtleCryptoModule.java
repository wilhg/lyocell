package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SubtleCryptoModule implements LyocellModule {
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String getName() {
        return "lyocell/subtle";
    }

    @Override
    public String getJsSource() {
        return ""; // Not directly imported
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellSubtleCrypto", this);
    }

    @HostAccess.Export
    public Object digest(String algorithm, Value data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(normalizeAlgo(algorithm));
            byte[] result;
            if (data.isString()) {
                result = digest.digest(data.asString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else if (data.hasBufferElements()) {
                ByteBuffer buffer = data.as(ByteBuffer.class);
                digest.update(buffer);
                result = digest.digest();
            } else if (data.hasArrayElements()) {
                byte[] bytes = new byte[(int) data.getArraySize()];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) data.getArrayElement(i).asInt();
                }
                result = digest.digest(bytes);
            } else {
                throw new IllegalArgumentException("Unsupported data type for digest");
            }
            return CompletableFuture.completedFuture(result);
        } catch (NoSuchAlgorithmException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @HostAccess.Export
    public Object generateKey(Value algorithm, boolean extractable, Value keyUsages) {
        try {
            String algoName = algorithm.isString() ? algorithm.asString() : algorithm.getMember("name").asString();
            int length = algorithm.hasMember("length") ? algorithm.getMember("length").asInt() : 256;

            KeyGenerator keyGen = KeyGenerator.getInstance(normalizeKeyAlgo(algoName));
            keyGen.init(length, secureRandom);
            SecretKey secretKey = keyGen.generateKey();
            
            return CompletableFuture.completedFuture(new CryptoKeyWrapper(secretKey, algoName, extractable, keyUsages));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @HostAccess.Export
    public Object encrypt(Value algorithm, CryptoKeyWrapper key, Value data) {
        try {
            String algoName = algorithm.isString() ? algorithm.asString() : algorithm.getMember("name").asString();
            Cipher cipher = Cipher.getInstance(normalizeCipherAlgo(algoName));
            
            if (algoName.equalsIgnoreCase("AES-GCM")) {
                byte[] iv = algorithm.getMember("iv").as(byte[].class);
                int tagLength = algorithm.hasMember("tagLength") ? algorithm.getMember("tagLength").asInt() : 128;
                GCMParameterSpec spec = new GCMParameterSpec(tagLength, iv);
                cipher.init(Cipher.ENCRYPT_MODE, key.getKey(), spec);
            } else if (algoName.equalsIgnoreCase("AES-CBC")) {
                byte[] iv = algorithm.getMember("iv").as(byte[].class);
                javax.crypto.spec.IvParameterSpec spec = new javax.crypto.spec.IvParameterSpec(iv);
                cipher.init(Cipher.ENCRYPT_MODE, key.getKey(), spec);
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, key.getKey());
            }

            byte[] input = data.as(byte[].class);
            byte[] output = cipher.doFinal(input);
            return CompletableFuture.completedFuture(output);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @HostAccess.Export
    public Object decrypt(Value algorithm, CryptoKeyWrapper key, Value data) {
        try {
            String algoName = algorithm.isString() ? algorithm.asString() : algorithm.getMember("name").asString();
            Cipher cipher = Cipher.getInstance(normalizeCipherAlgo(algoName));
            
            if (algoName.equalsIgnoreCase("AES-GCM")) {
                byte[] iv = algorithm.getMember("iv").as(byte[].class);
                int tagLength = algorithm.hasMember("tagLength") ? algorithm.getMember("tagLength").asInt() : 128;
                GCMParameterSpec spec = new GCMParameterSpec(tagLength, iv);
                cipher.init(Cipher.DECRYPT_MODE, key.getKey(), spec);
            } else if (algoName.equalsIgnoreCase("AES-CBC")) {
                byte[] iv = algorithm.getMember("iv").as(byte[].class);
                javax.crypto.spec.IvParameterSpec spec = new javax.crypto.spec.IvParameterSpec(iv);
                cipher.init(Cipher.DECRYPT_MODE, key.getKey(), spec);
            } else {
                cipher.init(Cipher.DECRYPT_MODE, key.getKey());
            }

            byte[] input = data.as(byte[].class);
            byte[] output = cipher.doFinal(input);
            return CompletableFuture.completedFuture(output);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @HostAccess.Export
    public Object importKey(String format, Value keyData, Value algorithm, boolean extractable, Value keyUsages) {
        try {
            String algoName = algorithm.isString() ? algorithm.asString() : algorithm.getMember("name").asString();
            Key key;
            if (format.equalsIgnoreCase("raw")) {
                byte[] bytes = keyData.as(byte[].class);
                key = new SecretKeySpec(bytes, normalizeKeyAlgo(algoName));
            } else {
                throw new UnsupportedOperationException("Format " + format + " not yet supported");
            }
            return CompletableFuture.completedFuture(new CryptoKeyWrapper(key, algoName, extractable, keyUsages));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String normalizeAlgo(String algo) {
        return switch (algo.toUpperCase().replace("-", "")) {
            case "SHA1" -> "SHA-1";
            case "SHA256" -> "SHA-256";
            case "SHA384" -> "SHA-384";
            case "SHA512" -> "SHA-512";
            case "MD5" -> "MD5";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + algo);
        };
    }

    private String normalizeKeyAlgo(String algo) {
        if (algo.toUpperCase().startsWith("AES")) return "AES";
        if (algo.equalsIgnoreCase("HMAC")) return "HmacSHA256";
        return algo;
    }

    private String normalizeCipherAlgo(String algo) {
        if (algo.equalsIgnoreCase("AES-GCM")) return "AES/GCM/NoPadding";
        if (algo.equalsIgnoreCase("AES-CBC")) return "AES/CBC/PKCS5Padding";
        return algo;
    }

    public static class CryptoKeyWrapper {
        private final Key key;
        private final String algorithm;
        private final boolean extractable;
        private final Value usages;

        public CryptoKeyWrapper(Key key, String algorithm, boolean extractable, Value usages) {
            this.key = key;
            this.algorithm = algorithm;
            this.extractable = extractable;
            this.usages = usages;
        }

        public Key getKey() { return key; }

        @HostAccess.Export public String getAlgorithm() { return algorithm; }
        @HostAccess.Export public boolean isExtractable() { return extractable; }
        @HostAccess.Export public Value getUsages() { return usages; }
    }
}

