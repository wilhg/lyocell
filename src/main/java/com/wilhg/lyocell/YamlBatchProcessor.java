package com.wilhg.lyocell;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlBatchProcessor {

  private final ObjectMapper mapper;

  public YamlBatchProcessor() {
    this.mapper = new ObjectMapper(new YAMLFactory());
  }

  public List<HttpRequest> loadBatchRequests(String yamlFilePath) throws IOException {
    File yamlFile = new File(yamlFilePath);
    if (!yamlFile.exists()) {
      throw new IOException("YAML file not found: " + yamlFilePath);
    }

    BatchRequest batchRequest = mapper.readValue(yamlFile, BatchRequest.class);
    return convertToHttpRequests(batchRequest);
  }

  private List<HttpRequest> convertToHttpRequests(BatchRequest batchRequest) {
    List<HttpRequest> httpRequests = new ArrayList<>();
    BatchRequest.RequestDefaults defaults = batchRequest.getDefaults();

    for (BatchRequest.RequestDefinition reqDef : batchRequest.getRequests()) {
      HttpRequest httpRequest = buildHttpRequest(reqDef, defaults);
      httpRequests.add(httpRequest);
    }

    return httpRequests;
  }

  private HttpRequest buildHttpRequest(BatchRequest.RequestDefinition reqDef,
                                        BatchRequest.RequestDefaults defaults) {
    // Method (default to GET)
    String method = reqDef.getMethod() != null ? reqDef.getMethod() : "GET";

    // URL (required)
    String url = reqDef.getUrl();
    if (url == null || url.isEmpty()) {
      throw new IllegalArgumentException("URL is required for request: " + reqDef.getName());
    }

    // Headers (merge defaults with request-specific)
    Map<String, String> headers = new HashMap<>();
    if (defaults != null && defaults.getHeaders() != null) {
      headers.putAll(defaults.getHeaders());
    }
    if (reqDef.getHeaders() != null) {
      headers.putAll(reqDef.getHeaders());
    }

    // Query parameters
    Map<String, String> queryParams = reqDef.getQueryParams();

    // Body (convert to JSON string if object)
    String body = null;
    if (reqDef.getBody() != null) {
      if (reqDef.getBody() instanceof String) {
        body = (String) reqDef.getBody();
      } else {
        try {
          body = new ObjectMapper().writeValueAsString(reqDef.getBody());
        } catch (Exception e) {
          throw new RuntimeException("Failed to serialize request body to JSON", e);
        }
      }
    }

    // Timeout (use request-specific, then default, then 5000ms)
    int timeout = 5000;
    if (reqDef.getTimeout() != null) {
      timeout = reqDef.getTimeout();
    } else if (defaults != null && defaults.getTimeout() != null) {
      timeout = defaults.getTimeout();
    }

    return new HttpRequest(
        method,
        url,
        queryParams,
        headers.isEmpty() ? null : headers,
        body,
        timeout
    );
  }
}
