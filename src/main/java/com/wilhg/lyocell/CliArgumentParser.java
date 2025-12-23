package com.wilhg.lyocell;

import java.util.HashMap;
import java.util.Map;

public class CliArgumentParser {

  private String method = "GET";
  private String url;
  private final Map<String, String> headers = new HashMap<>();
  private final Map<String, String> queryParams = new HashMap<>();
  private String body;
  private int timeout = 5000;
  private int concurrency = 1;  // Number of concurrent requests
  private int requests = 1;     // Total number of requests
  private String yamlFile;      // Path to YAML batch file

  public static CliArgumentParser parse(String[] args) {
    CliArgumentParser parser = new CliArgumentParser();

    if (args.length == 0) {
      throw new IllegalArgumentException("Usage: lyocell [METHOD] URL [ITEM [ITEM]]");
    }

    int index = 0;

    // Check if first arg is a YAML file (ends with .yaml or .yml)
    String firstArg = args[index];
    if (firstArg.endsWith(".yaml") || firstArg.endsWith(".yml")) {
      parser.yamlFile = firstArg;
      return parser; // Return early for batch mode
    }

    // Check if first arg is HTTP method
    firstArg = args[index].toUpperCase();
    if (firstArg.equals("GET") || firstArg.equals("POST") ||
        firstArg.equals("PUT") || firstArg.equals("DELETE") ||
        firstArg.equals("PATCH") || firstArg.equals("HEAD")) {
      parser.method = firstArg;
      index++;
    }

    // Next must be URL
    if (index >= args.length) {
      throw new IllegalArgumentException("URL is required");
    }
    parser.url = args[index++];

    // Parse remaining items (headers, query params, body)
    while (index < args.length) {
      String item = args[index++];

      if (item.startsWith("--timeout=")) {
        parser.timeout = Integer.parseInt(item.substring("--timeout=".length()));
      } else if (item.equals("--timeout") && index < args.length) {
        parser.timeout = Integer.parseInt(args[index++]);
      } else if (item.startsWith("-to=")) {
        parser.timeout = Integer.parseInt(item.substring("-to=".length()));
      } else if (item.equals("-to") && index < args.length) {
        parser.timeout = Integer.parseInt(args[index++]);
      } else if (item.startsWith("--concurrency=")) {
        parser.concurrency = Integer.parseInt(item.substring("--concurrency=".length()));
      } else if (item.equals("--concurrency") && index < args.length) {
        parser.concurrency = Integer.parseInt(args[index++]);
      } else if (item.startsWith("-c=")) {
        parser.concurrency = Integer.parseInt(item.substring("-c=".length()));
      } else if (item.equals("-c") && index < args.length) {
        parser.concurrency = Integer.parseInt(args[index++]);
      } else if (item.startsWith("--requests=")) {
        parser.requests = Integer.parseInt(item.substring("--requests=".length()));
      } else if (item.equals("--requests") && index < args.length) {
        parser.requests = Integer.parseInt(args[index++]);
      } else if (item.startsWith("-n=")) {
        parser.requests = Integer.parseInt(item.substring("-n=".length()));
      } else if (item.equals("-n") && index < args.length) {
        parser.requests = Integer.parseInt(args[index++]);
      } else if (item.contains(":=")) {
        // JSON field: field:=value (raw JSON)
        String[] parts = item.split(":=", 2);
        if (parser.body == null) {
          parser.body = "{";
        } else {
          parser.body += ",";
        }
        parser.body += "\"" + parts[0] + "\":" + parts[1];
      } else if (item.contains("==")) {
        // Query parameter: param==value
        String[] parts = item.split("==", 2);
        parser.queryParams.put(parts[0], parts[1]);
      } else if (item.contains(":")) {
        // Header: Header:value
        String[] parts = item.split(":", 2);
        parser.headers.put(parts[0], parts[1].trim());
      } else if (item.contains("=")) {
        // JSON field: field=value (string)
        String[] parts = item.split("=", 2);
        if (parser.body == null) {
          parser.body = "{";
        } else {
          parser.body += ",";
        }
        parser.body += "\"" + parts[0] + "\":\"" + parts[1] + "\"";
      }
    }

    // Close JSON body if exists
    if (parser.body != null) {
      parser.body += "}";
    }

    return parser;
  }

  public HttpRequest toHttpRequest() {
    return new HttpRequest(
        method,
        url,
        queryParams.isEmpty() ? null : queryParams,
        headers.isEmpty() ? null : headers,
        body,
        timeout
    );
  }

  public String getMethod() {
    return method;
  }

  public String getUrl() {
    return url;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public Map<String, String> getQueryParams() {
    return queryParams;
  }

  public String getBody() {
    return body;
  }

  public int getTimeout() {
    return timeout;
  }

  public int getConcurrency() {
    return concurrency;
  }

  public int getRequests() {
    return requests;
  }

  public String getYamlFile() {
    return yamlFile;
  }

  public boolean isBatchMode() {
    return yamlFile != null;
  }
}
