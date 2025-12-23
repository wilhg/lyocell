package com.wilhg.lyocell.modules;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryTest {
    @Test
    void testLoadModules() {
        List<LyocellModule> modules = ModuleRegistry.loadModules();
        assertNotNull(modules);
        // We registered 4 modules
        assertTrue(modules.size() >= 4, "Should have loaded at least 4 modules via ServiceLoader");
        
        boolean hasHttp = modules.stream().anyMatch(m -> m instanceof HttpModule);
        boolean hasCore = modules.stream().anyMatch(m -> m instanceof CoreModule);
        boolean hasMetrics = modules.stream().anyMatch(m -> m instanceof MetricsModule);
        boolean hasConsole = modules.stream().anyMatch(m -> m instanceof ConsoleModule);
        
        assertTrue(hasHttp, "HttpModule should be loaded");
        assertTrue(hasCore, "CoreModule should be loaded");
        assertTrue(hasMetrics, "MetricsModule should be loaded");
        assertTrue(hasConsole, "ConsoleModule should be loaded");
    }
}
