package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;

/**
 * Interface for Lyocell modules that can be registered in the JS engine.
 */
public interface LyocellModule {
    /**
     * Returns the name of the k6 module (e.g., "k6/http").
     */
    String getName();

    /**
     * Returns the synthetic JS source code for this module.
     */
    String getJsSource();

    /**
     * Installs the module into the GraalJS context.
     * 
     * @param context The GraalJS context to install into.
     * @param moduleContext The context providing dependencies.
     */
    void install(Context context, ModuleContext moduleContext);

    /**
     * Optional cleanup when the module is no longer needed (e.g., when the VU finishes).
     */
    default void close() {}
}
