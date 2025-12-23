package com.wilhg.lyocell.modules;

import org.graalvm.polyglot.Context;

/**
 * Interface for Lyocell modules that can be registered in the JS engine.
 */
public interface LyocellModule {
    /**
     * Installs the module into the GraalJS context.
     * 
     * @param context The GraalJS context to install into.
     * @param moduleContext The context providing dependencies.
     */
    void install(Context context, ModuleContext moduleContext);
}
