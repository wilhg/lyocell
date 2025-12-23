package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.metrics.MetricsCollector;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Registry for all available Lyocell modules.
 */
public class ModuleRegistry {
    /**
     * Returns a list of default modules that should be available in every JS context.
     * These are hardcoded to ensure core functionality is always present.
     * 
     * @param metricsCollector The metrics collector to be used by modules.
     * @return A list of Lyocell modules.
     */
    public static List<LyocellModule> getDefaultModules(MetricsCollector metricsCollector) {
        List<LyocellModule> modules = new ArrayList<>();
        modules.add(new HttpModule(metricsCollector));
        modules.add(new CoreModule(metricsCollector));
        modules.add(new MetricsModule(metricsCollector));
        modules.add(new ConsoleModule());
        return modules;
    }

    /**
     * Loads modules using ServiceLoader.
     * 
     * @return A list of discovered Lyocell modules.
     */
    public static List<LyocellModule> loadModules() {
        List<LyocellModule> modules = new ArrayList<>();
        ServiceLoader<LyocellModule> loader = ServiceLoader.load(LyocellModule.class);
        for (LyocellModule module : loader) {
            modules.add(module);
        }
        return modules;
    }
}
