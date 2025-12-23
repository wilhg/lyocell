package com.wilhg.lyocell;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Main {

  public static void main(String[] args) throws InterruptedException {
    LyocellCli cli = new LyocellCli();

    if (args.length == 0) {
      // Demo mode - submit sample tasks
      runDemoMode(cli);
    } else {
      // Parse and execute request(s) from CLI args
      try {
        CliArgumentParser parser = CliArgumentParser.parse(args);

        if (parser.isBatchMode()) {
          // Batch mode - load requests from YAML file
          runBatchMode(cli, parser);
        } else {
          HttpRequest request = parser.toHttpRequest();

          if (parser.getRequests() > 1 || parser.getConcurrency() > 1) {
            // Load testing mode
            runLoadTest(cli, parser, request);
          } else {
            // Single request mode
            runSingleRequest(cli, parser, request);
          }
        }
      } catch (IllegalArgumentException | IOException e) {
        System.err.println("Error: " + e.getMessage());
        printUsage();
        System.exit(1);
      }
    }

    cli.shutdown();
  }

  private static void runSingleRequest(LyocellCli cli, CliArgumentParser parser, HttpRequest request) throws InterruptedException {
    cli.submitTask(request);

    // Run UI until request completes
    Thread uiThread = new Thread(() -> cli.runUI());
    uiThread.start();

    // Wait for task to complete
    Thread.sleep(parser.getTimeout() + 1000);

    // Print result
    var tasks = cli.requestOverview.getAllTasks();
    if (!tasks.isEmpty()) {
      RequestTask task = tasks.iterator().next();
      System.out.println("\n\n=== Result ===");
      System.out.println("Status: " + task.getStatus());
      System.out.println("HTTP Status: " + task.getHttpStatus());
      System.out.println("Response Body:\n" + task.getResponseBody());
    }

    uiThread.interrupt();
  }

  private static void runLoadTest(LyocellCli cli, CliArgumentParser parser, HttpRequest request) throws InterruptedException {
    int totalRequests = parser.getRequests();
    int concurrency = parser.getConcurrency();

    System.out.println("Starting load test:");
    System.out.println("  Total requests: " + totalRequests);
    System.out.println("  Concurrency: " + concurrency);
    System.out.println("  URL: " + request.url());
    System.out.println();

    long startTime = System.currentTimeMillis();

    // Submit requests in batches based on concurrency
    Thread submitterThread = new Thread(() -> {
      try {
        for (int i = 0; i < totalRequests; i++) {
          cli.submitTask(request);

          // Control concurrency by limiting batch size
          if ((i + 1) % concurrency == 0) {
            Thread.sleep(10); // Small pause between batches
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    submitterThread.start();

    // Run UI until all requests complete
    Thread uiThread = new Thread(() -> cli.runUI());
    uiThread.start();

    // Wait for all requests to complete
    submitterThread.join();

    // Wait for all tasks to actually complete
    long waitStart = System.currentTimeMillis();
    long maxWaitTime = parser.getTimeout() + 2000;

    while (System.currentTimeMillis() - waitStart < maxWaitTime) {
      var stats = cli.requestOverview.getStatistics();
      long completed = stats.getOrDefault(TaskStatus.SUCCEED, 0L)
          + stats.getOrDefault(TaskStatus.FAILED, 0L);

      if (completed >= totalRequests) {
        // Wait for at least one UI refresh cycle (500ms) to ensure final state is displayed
        Thread.sleep(600);
        break;
      }
      Thread.sleep(100);
    }

    uiThread.interrupt();

    // Print summary
    long endTime = System.currentTimeMillis();
    long totalTime = endTime - startTime;

    var stats = cli.requestOverview.getStatistics();
    long successful = stats.getOrDefault(TaskStatus.SUCCEED, 0L);
    long failed = stats.getOrDefault(TaskStatus.FAILED, 0L);

    // Calculate actual average request duration
    var allTasks = cli.requestOverview.getAllTasks();
    double avgRequestTime = allTasks.stream()
        .filter(task -> task.getStartedAt() != null && task.getCompletedAt() != null)
        .mapToLong(task -> java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis())
        .average()
        .orElse(0.0);

    System.out.println("\n\n=== Load Test Results ===");
    System.out.println("Total time: " + totalTime + "ms");
    System.out.println("Requests: " + totalRequests);
    System.out.println("Successful: " + successful);
    System.out.println("Failed: " + failed);
    System.out.println("Requests/sec: " + String.format("%.2f", (totalRequests * 1000.0) / totalTime));
    System.out.println("Avg time per request: " + String.format("%.2f", avgRequestTime) + "ms");
  }

  private static void runBatchMode(LyocellCli cli, CliArgumentParser parser) throws InterruptedException, IOException {
    YamlBatchProcessor processor = new YamlBatchProcessor();
    List<HttpRequest> requests = processor.loadBatchRequests(parser.getYamlFile());

    System.out.println("Starting batch mode:");
    System.out.println("  Total requests: " + requests.size());
    System.out.println("  Source: " + parser.getYamlFile());
    System.out.println();

    long startTime = System.currentTimeMillis();

    // Submit all requests
    Thread submitterThread = new Thread(() -> {
      try {
        for (HttpRequest request : requests) {
          cli.submitTask(request);
          Thread.sleep(10); // Small delay between submissions
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    submitterThread.start();

    // Run UI until all requests complete
    Thread uiThread = new Thread(() -> cli.runUI());
    uiThread.start();

    // Wait for all requests to complete
    submitterThread.join();

    // Wait for all tasks to actually complete
    int expectedTasks = requests.size();
    int maxTimeout = requests.stream()
        .mapToInt(HttpRequest::timeout)
        .max()
        .orElse(5000);

    long waitStart = System.currentTimeMillis();
    long maxWaitTime = maxTimeout + 2000;

    while (System.currentTimeMillis() - waitStart < maxWaitTime) {
      var stats = cli.requestOverview.getStatistics();
      long completed = stats.getOrDefault(TaskStatus.SUCCEED, 0L)
          + stats.getOrDefault(TaskStatus.FAILED, 0L);

      if (completed >= expectedTasks) {
        // Wait for at least one UI refresh cycle (500ms) to ensure final state is displayed
        Thread.sleep(600);
        break;
      }
      Thread.sleep(100);
    }

    uiThread.interrupt();

    // Print summary
    long endTime = System.currentTimeMillis();
    long totalTime = endTime - startTime;

    var stats = cli.requestOverview.getStatistics();
    long successful = stats.getOrDefault(TaskStatus.SUCCEED, 0L);
    long failed = stats.getOrDefault(TaskStatus.FAILED, 0L);

    // Calculate actual average request duration
    var allTasks = cli.requestOverview.getAllTasks();
    double avgRequestTime = allTasks.stream()
        .filter(task -> task.getStartedAt() != null && task.getCompletedAt() != null)
        .mapToLong(task -> java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis())
        .average()
        .orElse(0.0);

    System.out.println("\n\n=== Batch Results ===");
    System.out.println("Total time: " + totalTime + "ms");
    System.out.println("Requests: " + requests.size());
    System.out.println("Successful: " + successful);
    System.out.println("Failed: " + failed);
    System.out.println("Avg time per request: " + String.format("%.2f", avgRequestTime) + "ms");
  }

  private static void runDemoMode(LyocellCli cli) throws InterruptedException {
    // Submit some demo tasks
    Thread submitterThread = new Thread(() -> {
      try {
        // Submit 10 tasks with different delays
        for (int i = 0; i < 10; i++) {
          HttpRequest request = new HttpRequest(
              "GET",
              "https://httpbin.org/delay/" + (i % 3),
              Map.of("task", String.valueOf(i)),
              Map.of("User-Agent", "Lyocell-CLI"),
              null,
              10000
          );
          cli.submitTask(request);
          Thread.sleep(500); // Submit a new task every 500ms
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });

    submitterThread.start();

    // Run the UI (blocks until Ctrl+C)
    cli.runUI();

    // Cleanup
    submitterThread.interrupt();
  }

  private static void printUsage() {
    System.out.println("\nLyocell - Modern HTTP Client CLI\n");
    System.out.println("Usage:");
    System.out.println("  lyocell [METHOD] URL [REQUEST_ITEM [REQUEST_ITEM ...]]");
    System.out.println("  lyocell <yaml-file>\n");
    System.out.println("Examples:");
    System.out.println("  # Simple GET request");
    System.out.println("  lyocell GET https://httpbin.org/get");
    System.out.println();
    System.out.println("  # GET with query parameters");
    System.out.println("  lyocell https://httpbin.org/get name==John age==30");
    System.out.println();
    System.out.println("  # POST with JSON data");
    System.out.println("  lyocell POST https://httpbin.org/post name=John age:=30");
    System.out.println();
    System.out.println("  # With custom headers");
    System.out.println("  lyocell https://httpbin.org/get User-Agent:Lyocell Authorization:\"Bearer token\"");
    System.out.println();
    System.out.println("  # With timeout");
    System.out.println("  lyocell https://httpbin.org/delay/2 --timeout=3000");
    System.out.println();
    System.out.println("  # Batch requests from YAML file");
    System.out.println("  lyocell requests.yaml");
    System.out.println();
    System.out.println("Request Items:");
    System.out.println("  key=value          JSON string field");
    System.out.println("  key:=value         JSON raw/number field");
    System.out.println("  key==value         URL query parameter");
    System.out.println("  Header:value       Request header");
    System.out.println();
    System.out.println("Options:");
    System.out.println("  -to, --timeout=ms       Request timeout in milliseconds (default: 5000)");
    System.out.println("  -n, --requests=N        Number of requests to send (default: 1)");
    System.out.println("  -c, --concurrency=N     Number of concurrent requests (default: 1)");
    System.out.println();
    System.out.println("Load Testing:");
    System.out.println("  # Send 100 requests with 10 concurrent");
    System.out.println("  lyocell https://httpbin.org/get -n=100 -c=10");
    System.out.println();
    System.out.println("Demo Mode:");
    System.out.println("  lyocell            Run without arguments for demo");
  }
}
