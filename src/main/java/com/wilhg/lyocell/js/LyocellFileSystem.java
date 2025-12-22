package com.wilhg.lyocell.js;

import org.graalvm.polyglot.io.FileSystem;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.Set;

public class LyocellFileSystem implements FileSystem {
    private final FileSystem delegate = FileSystem.newDefaultFileSystem();
    private static final String K6_PREFIX = "k6/";

    @Override
    public Path parsePath(URI uri) {
        if ("lyocell".equals(uri.getScheme())) {
            return Paths.get(uri.getSchemeSpecificPart());
        }
        return delegate.parsePath(uri);
    }

    @Override
    public Path parsePath(String path) {
        if (isVirtualModule(path)) {
            return Paths.get(path);
        }
        return delegate.parsePath(path);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        String pathStr = path.toString();
        if (isVirtualModule(pathStr)) {
            String content = getSyntheticModule(getModuleName(pathStr));
            return new ReadOnlyStringChannel(content);
        }
        return delegate.newByteChannel(path, options, attrs);
    }

    private boolean isVirtualModule(String path) {
        return path.equals("k6") || path.startsWith("k6/") || 
               path.endsWith("/k6") || path.contains("/k6/");
    }

    private String getModuleName(String path) {
        if (path.contains("k6/http")) return "k6/http";
        if (path.contains("k6")) return "k6";
        return path;
    }

    private String getSyntheticModule(String moduleName) {
        if (moduleName.equals("k6/http") || moduleName.endsWith("/k6/http")) {
            return """
                const Http = globalThis.LyocellHttp;
                export const get = (url, params) => Http.get(url, params);
                export const post = (url, body, params) => Http.post(url, body, params);
                export default { get, post };
                """;
        } else if (moduleName.equals("k6") || moduleName.endsWith("/k6")) {
            return """
                const Core = globalThis.LyocellCore;
                export const check = (val, sets, tags) => Core.check(val, sets, tags);
                export const sleep = (sec) => Core.sleep(sec);
                export default { check, sleep };
                """;
        }
        throw new IllegalArgumentException("Unknown lyocell module: " + moduleName);
    }

    // Boilerplate delegation
    @Override public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... options) throws IOException {
        if (isVirtualModule(path.toString())) return;
        delegate.checkAccess(path, modes, options);
    }
    @Override public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException { delegate.createDirectory(dir, attrs); }
    @Override public void delete(Path path) throws IOException { delegate.delete(path); }
    @Override public Path toAbsolutePath(Path path) {
        if (isVirtualModule(path.toString())) return path;
        return delegate.toAbsolutePath(path);
    }
    @Override public Path toRealPath(Path path, LinkOption... options) throws IOException {
        if (isVirtualModule(path.toString())) return path;
        return delegate.toRealPath(path, options);
    }
    @Override public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        if (isVirtualModule(path.toString())) {
            String content = getSyntheticModule(getModuleName(path.toString()));
            return Map.of("isRegularFile", true, "size", (long) content.length());
        }
        return delegate.readAttributes(path, attributes, options);
    }
    @Override public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException { return delegate.newDirectoryStream(dir, filter); }

    private static class ReadOnlyStringChannel implements SeekableByteChannel {
        private final byte[] content;
        private int position = 0;

        public ReadOnlyStringChannel(String content) { this.content = content.getBytes(); }
        @Override public int read(java.nio.ByteBuffer dst) {
            if (position >= content.length) return -1;
            int n = Math.min(dst.remaining(), content.length - position);
            dst.put(content, position, n);
            position += n;
            return n;
        }
        @Override public int write(java.nio.ByteBuffer src) { throw new UnsupportedOperationException(); }
        @Override public long position() { return position; }
        @Override public SeekableByteChannel position(long newPosition) { position = (int) newPosition; return this; }
        @Override public long size() { return content.length; }
        @Override public SeekableByteChannel truncate(long size) { throw new UnsupportedOperationException(); }
        @Override public boolean isOpen() { return true; }
        @Override public void close() {}
    }
}
