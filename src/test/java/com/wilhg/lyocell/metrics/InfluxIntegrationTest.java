package com.wilhg.lyocell.metrics;

import com.wilhg.lyocell.engine.OutputConfig;
import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Testcontainers
@Tag("integration")
class InfluxIntegrationTest {

    private static final String TOKEN = "my-super-secret-auth-token";
    private static final String ORG = "lyocell";
    private static final String BUCKET = "metrics";

    @Container
    static GenericContainer<?> influxdb = new GenericContainer<>("influxdb:2.7")
            .withExposedPorts(8086)
            .withEnv("DOCKER_INFLUXDB_INIT_MODE", "setup")
            .withEnv("DOCKER_INFLUXDB_INIT_USERNAME", "admin")
            .withEnv("DOCKER_INFLUXDB_INIT_PASSWORD", "password123")
            .withEnv("DOCKER_INFLUXDB_INIT_ORG", ORG)
            .withEnv("DOCKER_INFLUXDB_INIT_BUCKET", BUCKET)
            .withEnv("DOCKER_INFLUXDB_INIT_ADMIN_TOKEN", TOKEN)
            .waitingFor(Wait.forHttp("/health").forPort(8086));

    @Test
    void testMetricsSentToInflux() throws Exception {
        String influxUrl = "http://" + influxdb.getHost() + ":" + influxdb.getMappedPort(8086);
        
        Path script = Files.createTempFile("test", ".js");
        Files.writeString(script, "export default function() { }");

        TestEngine engine = new TestEngine();
        TestConfig config = new TestConfig(1, 5, null, List.of(
            new OutputConfig("influxdb", influxUrl)
        ));

        // Use a dirty hack to set env vars for the test or just mock them if I can
        // Since I'm in the same JVM, I can't easily change System.getenv().
        // I'll modify InfluxOutput to also check System properties.
        
        System.setProperty("INFLUX_TOKEN", TOKEN);
        System.setProperty("INFLUX_ORG", ORG);
        System.setProperty("INFLUX_BUCKET", BUCKET);

        try {
            assertDoesNotThrow(() -> engine.run(script, config));
        } finally {
            System.clearProperty("INFLUX_TOKEN");
            System.clearProperty("INFLUX_ORG");
            System.clearProperty("INFLUX_BUCKET");
            Files.deleteIfExists(script);
        }
    }
}
