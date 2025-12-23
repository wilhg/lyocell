package com.wilhg.lyocell.metrics;

import com.wilhg.lyocell.engine.OutputConfig;
import io.micrometer.influx.InfluxApiVersion;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import java.time.Duration;

public class InfluxOutput {
    public static InfluxMeterRegistry createRegistry(OutputConfig config) {
        InfluxConfig influxConfig = new InfluxConfig() {
            @Override
            public String get(String key) {
                // Map Micrometer keys to environment variables or defaults
                return switch (key) {
                    case "influx.uri" -> config.target().isEmpty() ? "http://localhost:8086" : config.target();
                    case "influx.token" -> getPropertyOrEnv("INFLUX_TOKEN");
                    case "influx.org" -> getPropertyOrEnv("INFLUX_ORG");
                    case "influx.bucket" -> getPropertyOrEnv("INFLUX_BUCKET");
                    case "influx.apiVersion" -> getPropertyOrEnv("INFLUX_VERSION") != null ? getPropertyOrEnv("INFLUX_VERSION") : "v2";
                    default -> null;
                };
            }

            private String getPropertyOrEnv(String name) {
                String val = System.getProperty(name);
                if (val == null) val = System.getenv(name);
                return val;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(1);
            }
        };

        return InfluxMeterRegistry.builder(influxConfig)
                .httpClient(new JdkHttpSender())
                .build();
    }
}
