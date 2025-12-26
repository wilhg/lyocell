package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FsModule implements LyocellModule {
    @Override
    public String getName() {
        return "lyocell/experimental/fs";
    }

    @Override
    public String getJsSource() {
        return """
            const Fs = globalThis.LyocellFs;
            export const open = (path) => Fs.open(path);
            export const stat = (path) => Fs.stat(path);
            export const readFile = (path) => Fs.readFile(path);
            export default { open, stat, readFile };
            """;
    }

    @Override
    public void install(Context context, ModuleContext moduleContext) {
        context.getBindings("js").putMember("LyocellFs", this);
    }

    @HostAccess.Export
    public Object readFile(String path) throws IOException {
        return Files.readString(Paths.get(path));
    }

    @HostAccess.Export
    public Object stat(String path) throws IOException {
        Path p = Paths.get(path);
        var attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
        return new StatWrapper(attrs);
    }

    public static class StatWrapper {
        @HostAccess.Export public final long size;
        @HostAccess.Export public final boolean isDirectory;

        public StatWrapper(java.nio.file.attribute.BasicFileAttributes attrs) {
            this.size = attrs.size();
            this.isDirectory = attrs.isDirectory();
        }
    }
}

