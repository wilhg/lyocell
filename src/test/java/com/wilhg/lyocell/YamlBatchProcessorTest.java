package com.wilhg.lyocell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlBatchProcessorTest {

  @TempDir
  Path tempDir;

  @Test
  void testLoadSimpleBatchRequests() throws IOException {
    String yaml = """
        requests:
          - name: Get Users
            method: GET
            url: https://api.example.com/users
          - name: Create User
            method: POST
            url: https://api.example.com/users
            body:
              name: John
              age: 30
        """;

    Path yamlFile = tempDir.resolve("requests.yaml");
    Files.writeString(yamlFile, yaml);

    YamlBatchProcessor processor = new YamlBatchProcessor();
    List<HttpRequest> requests = processor.loadBatchRequests(yamlFile.toString());

    assertEquals(2, requests.size());

    // First request
    HttpRequest req1 = requests.get(0);
    assertEquals("GET", req1.method());
    assertEquals("https://api.example.com/users", req1.url());
    assertNull(req1.body());

    // Second request
    HttpRequest req2 = requests.get(1);
    assertEquals("POST", req2.method());
    assertEquals("https://api.example.com/users", req2.url());
    assertNotNull(req2.body());
    assertTrue(req2.body().contains("John"));
    assertTrue(req2.body().contains("30"));
  }

  @Test
  void testLoadBatchRequestsWithDefaults() throws IOException {
    String yaml = """
        defaults:
          timeout: 10000
          headers:
            Authorization: Bearer token123
            User-Agent: Lyocell-Test
        requests:
          - name: Get Users
            url: https://api.example.com/users
          - name: Get Profile
            url: https://api.example.com/profile
            headers:
              X-Custom: custom-value
        """;

    Path yamlFile = tempDir.resolve("requests.yaml");
    Files.writeString(yamlFile, yaml);

    YamlBatchProcessor processor = new YamlBatchProcessor();
    List<HttpRequest> requests = processor.loadBatchRequests(yamlFile.toString());

    assertEquals(2, requests.size());

    // First request - should inherit defaults
    HttpRequest req1 = requests.get(0);
    assertEquals("GET", req1.method()); // Default method
    assertEquals(10000, req1.timeout()); // From defaults
    assertNotNull(req1.headers());
    assertEquals("Bearer token123", req1.headers().get("Authorization"));
    assertEquals("Lyocell-Test", req1.headers().get("User-Agent"));

    // Second request - should merge headers with defaults
    HttpRequest req2 = requests.get(1);
    assertEquals(10000, req2.timeout()); // From defaults
    assertNotNull(req2.headers());
    assertEquals("Bearer token123", req2.headers().get("Authorization"));
    assertEquals("Lyocell-Test", req2.headers().get("User-Agent"));
    assertEquals("custom-value", req2.headers().get("X-Custom"));
  }

  @Test
  void testLoadBatchRequestsWithQueryParams() throws IOException {
    String yaml = """
        requests:
          - name: Search Users
            url: https://api.example.com/users
            queryParams:
              q: john
              limit: "10"
              active: "true"
        """;

    Path yamlFile = tempDir.resolve("requests.yaml");
    Files.writeString(yamlFile, yaml);

    YamlBatchProcessor processor = new YamlBatchProcessor();
    List<HttpRequest> requests = processor.loadBatchRequests(yamlFile.toString());

    assertEquals(1, requests.size());

    HttpRequest req = requests.get(0);
    assertNotNull(req.queryParams());
    assertEquals("john", req.queryParams().get("q"));
    assertEquals("10", req.queryParams().get("limit"));
    assertEquals("true", req.queryParams().get("active"));
  }

  @Test
  void testLoadBatchRequestsWithCustomTimeout() throws IOException {
    String yaml = """
        defaults:
          timeout: 5000
        requests:
          - name: Quick Request
            url: https://api.example.com/quick
            timeout: 1000
          - name: Slow Request
            url: https://api.example.com/slow
            timeout: 30000
        """;

    Path yamlFile = tempDir.resolve("requests.yaml");
    Files.writeString(yamlFile, yaml);

    YamlBatchProcessor processor = new YamlBatchProcessor();
    List<HttpRequest> requests = processor.loadBatchRequests(yamlFile.toString());

    assertEquals(2, requests.size());
    assertEquals(1000, requests.get(0).timeout());
    assertEquals(30000, requests.get(1).timeout());
  }

  @Test
  void testLoadBatchRequestsFileNotFound() {
    YamlBatchProcessor processor = new YamlBatchProcessor();

    IOException exception = assertThrows(IOException.class, () ->
        processor.loadBatchRequests("/nonexistent/file.yaml")
    );

    assertTrue(exception.getMessage().contains("not found"));
  }

  @Test
  void testLoadBatchRequestsMissingUrl() throws IOException {
    String yaml = """
        requests:
          - name: Invalid Request
            method: GET
        """;

    Path yamlFile = tempDir.resolve("requests.yaml");
    Files.writeString(yamlFile, yaml);

    YamlBatchProcessor processor = new YamlBatchProcessor();

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        processor.loadBatchRequests(yamlFile.toString())
    );

    assertTrue(exception.getMessage().contains("URL is required"));
  }

  @Test
  void testLoadComplexBatchRequests() throws IOException {
    String yaml = """
        defaults:
          timeout: 8000
          headers:
            Content-Type: application/json
        requests:
          - name: Get All Users
            method: GET
            url: https://api.example.com/users
            queryParams:
              page: "1"
              size: "50"
          - name: Create User
            method: POST
            url: https://api.example.com/users
            headers:
              Authorization: Bearer token456
            body:
              name: Alice
              email: alice@example.com
              age: 25
          - name: Update User
            method: PUT
            url: https://api.example.com/users/123
            timeout: 15000
            body:
              name: Bob Updated
        """;

    Path yamlFile = tempDir.resolve("requests.yaml");
    Files.writeString(yamlFile, yaml);

    YamlBatchProcessor processor = new YamlBatchProcessor();
    List<HttpRequest> requests = processor.loadBatchRequests(yamlFile.toString());

    assertEquals(3, requests.size());

    // First request
    HttpRequest req1 = requests.get(0);
    assertEquals("GET", req1.method());
    assertEquals("https://api.example.com/users", req1.url());
    assertEquals(8000, req1.timeout());
    assertNotNull(req1.queryParams());
    assertEquals("1", req1.queryParams().get("page"));

    // Second request
    HttpRequest req2 = requests.get(1);
    assertEquals("POST", req2.method());
    assertEquals(8000, req2.timeout());
    assertNotNull(req2.headers());
    assertEquals("Bearer token456", req2.headers().get("Authorization"));
    assertNotNull(req2.body());
    assertTrue(req2.body().contains("Alice"));

    // Third request
    HttpRequest req3 = requests.get(2);
    assertEquals("PUT", req3.method());
    assertEquals(15000, req3.timeout()); // Override timeout
    assertNotNull(req3.body());
    assertTrue(req3.body().contains("Bob Updated"));
  }
}
