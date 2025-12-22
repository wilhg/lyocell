package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.HostAccess;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ConsoleModule {
    @HostAccess.Export
    public void log(Object... args) {
        String message = Arrays.stream(args)
                .map(String::valueOf)
                .collect(Collectors.joining(" "));
        System.out.println(message);
    }
}
