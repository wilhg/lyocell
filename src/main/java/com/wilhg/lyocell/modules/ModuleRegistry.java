package com.wilhg.lyocell.modules;

import com.wilhg.lyocell.metrics.MetricsCollector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for all available Lyocell modules.
 */
public class ModuleRegistry {
    private static final Map<String, LyocellModule> modulesByName = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    private static synchronized void ensureInitialized(MetricsCollector metricsCollector) {
        if (!initialized) {
            // Load from ServiceLoader
            ServiceLoader<LyocellModule> loader = ServiceLoader.load(LyocellModule.class);
            for (LyocellModule module : loader) {
                // If the module needs metricsCollector, we might have an issue since ServiceLoader
                // uses the default constructor.
                // We'll rely on getDefaultModules for core modules and load others via ServiceLoader.
                modulesByName.put(module.getName(), module);
            }

            // Core modules usually need metricsCollector, so we re-instantiate them if needed
            // or just ensure they are in the map.
            for (LyocellModule m : getDefaultModules(metricsCollector)) {
                modulesByName.put(m.getName(), m);
            }
            initialized = true;
        }
    }

    public static String getModuleJs(String name, MetricsCollector metricsCollector) {
        ensureInitialized(metricsCollector);
        
        // Normalize name: k6/http -> lyocell/http, k6 -> lyocell
        String normalizedName = name;
        if (name.equals("k6")) {
            normalizedName = "lyocell";
        } else if (name.startsWith("k6/")) {
            normalizedName = "lyocell/" + name.substring(3);
        }

        // Match lyocell/http even if name is full path
        for (Map.Entry<String, LyocellModule> entry : modulesByName.entrySet()) {
            if (normalizedName.equals(entry.getKey()) || normalizedName.endsWith("/" + entry.getKey())) {
                return entry.getValue().getJsSource();
            }
        }
        return null;
    }

    public static List<LyocellModule> getAllModules(MetricsCollector metricsCollector) {
        ensureInitialized(metricsCollector);
        return new ArrayList<>(modulesByName.values());
    }

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
        modules.add(new EncodingModule());
        modules.add(new CryptoModule());
        modules.add(new ExecutionModule());
        modules.add(new DataModule());
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
