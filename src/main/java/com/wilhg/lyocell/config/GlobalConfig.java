package com.wilhg.lyocell.config;

import com.wilhg.lyocell.engine.OutputConfig;
import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record GlobalConfig(List<OutputConfig> outputs) {

    private static final String ENV_PROMETHEUS_URL = "LYOCELL_PROMETHEUS_URL";
    private static final String CONFIG_DIR = ".lyocell";
    private static final String CONFIG_FILE = "config.yaml";

    public static GlobalConfig load() {
        List<OutputConfig> outputs = new ArrayList<>();
        Set<String> overriddenTypes = new HashSet<>();

        // 1. Load from Environment (High Priority)
        loadPrometheusFromEnv().ifPresent(config -> {
            outputs.add(config);
            overriddenTypes.add("prometheus");
        });

        // 2. Load from YAML (Skip overridden types)
        outputs.addAll(loadFromYaml(overriddenTypes));

        return new GlobalConfig(Collections.unmodifiableList(outputs));
    }

    private static Optional<OutputConfig> loadPrometheusFromEnv() {
        String url = System.getenv(ENV_PROMETHEUS_URL);
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        // Only URL from environment, no separate auth variables for security
        return createOutputConfig("prometheus", url, null, null);
    }

    @SuppressWarnings("unchecked")
    private static List<OutputConfig> loadFromYaml(Set<String> overriddenTypes) {
        Path configPath = Paths.get(System.getProperty("user.home"), CONFIG_DIR, CONFIG_FILE);
        File configFile = configPath.toFile();
        
        if (!configFile.exists()) {
            return Collections.emptyList();
        }

        List<OutputConfig> configs = new ArrayList<>();
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlData = yaml.load(inputStream);
            
            if (yamlData != null && yamlData.containsKey("outputs")) {
                List<Map<String, Object>> outputsList = (List<Map<String, Object>>) yamlData.get("outputs");
                for (Map<String, Object> out : outputsList) {
                    String type = (String) out.get("type");
                    
                    if (overriddenTypes.contains(type)) {
                        continue;
                    }

                    String url = (String) out.get("url");
                    String username = (String) out.get("username");
                    String password = (String) out.get("password");

                    createOutputConfig(type, url, username, password).ifPresent(configs::add);
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to load config from " + configPath + ": " + e.getMessage());
        }
        return configs;
    }

    private static Optional<OutputConfig> createOutputConfig(String type, String url, String username, String password) {
        if (type == null || url == null) {
            return Optional.empty();
        }

        String finalUrl = url;
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            try {
                finalUrl = injectAuth(url, username, password);
            } catch (URISyntaxException e) {
                System.err.println("Warning: Failed to inject auth into URL (" + url + "): " + e.getMessage());
                // Fallback to original URL, though it might fail later if auth is required
            }
        }

        return Optional.of(new OutputConfig(type, finalUrl));
    }

    private static String injectAuth(String originalUrl, String username, String password) throws URISyntaxException {
        URI uri = new URI(originalUrl);
        String userInfo = username + ":" + password;
        // Reconstruct URI with userInfo
        return new URI(uri.getScheme(), userInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
    }
}