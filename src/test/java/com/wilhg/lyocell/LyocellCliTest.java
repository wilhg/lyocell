package com.wilhg.lyocell;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class LyocellCliTest {

  @Container
  private static final GenericContainer<?> httpbin = new GenericContainer<>("sharat87/httpbun")
      .withExposedPorts(80);

  private LyocellCli cli;
  private String httpbinUrl;

  @BeforeEach
  void setUp() {
    cli = new LyocellCli();
    httpbinUrl = "http://" + httpbin.getHost() + ":" + httpbin.getMappedPort(80);
  }

  @AfterEach
  void tearDown() {
    cli.shutdown();
  }

  @Test
  void testSubmitTask() throws InterruptedException {
    HttpRequest request = new HttpRequest(
        "GET",
        httpbinUrl + "/get",
        null,
        Map.of("User-Agent", "Test"),
        null,
        5000
    );

    cli.submitTask(request);

    // Wait for task to complete
    Thread.sleep(2000);

    // Verify task was processed (check via RequestOverview)
    var allTasks = cli.requestOverview.getAllTasks();
    assertEquals(1, allTasks.size());
  }

  @Test
  void testMultipleTaskSubmissions() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(5);
    AtomicInteger successCount = new AtomicInteger(0);

    cli.requestOverview.addListener(new TaskStatusListener() {
      @Override
      public void onTaskSubmitted(RequestTask task) {}

      @Override
      public void onTaskStarted(RequestTask task) {}

      @Override
      public void onTaskSucceed(RequestTask task) {
        successCount.incrementAndGet();
        latch.countDown();
      }

      @Override
      public void onTaskFailed(RequestTask task) {
        latch.countDown();
      }
    });

    // Submit 5 tasks
    for (int i = 0; i < 5; i++) {
      HttpRequest request = new HttpRequest(
          "GET",
          httpbinUrl + "/get",
          Map.of("task", String.valueOf(i)),
          null,
          null,
          5000
      );
      cli.submitTask(request);
    }

    assertTrue(latch.await(15, TimeUnit.SECONDS));
    assertEquals(5, successCount.get());
  }

  @Test
  void testCliWithFailedRequests() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger failedCount = new AtomicInteger(0);

    cli.requestOverview.addListener(new TaskStatusListener() {
      @Override
      public void onTaskSubmitted(RequestTask task) {}

      @Override
      public void onTaskStarted(RequestTask task) {}

      @Override
      public void onTaskSucceed(RequestTask task) {
        latch.countDown();
      }

      @Override
      public void onTaskFailed(RequestTask task) {
        failedCount.incrementAndGet();
        latch.countDown();
      }
    });

    // Submit 1 successful request
    cli.submitTask(new HttpRequest("GET", httpbinUrl + "/get", null, null, null, 5000));

    // Submit 1 failed request
    cli.submitTask(new HttpRequest("GET", "http://invalid-host.test", null, null, null, 2000));

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals(1, failedCount.get());
  }

  @Test
  void testCliStatisticsTracking() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    cli.requestOverview.addListener(new TaskStatusListener() {
      @Override
      public void onTaskSubmitted(RequestTask task) {}

      @Override
      public void onTaskStarted(RequestTask task) {}

      @Override
      public void onTaskSucceed(RequestTask task) {
        latch.countDown();
      }

      @Override
      public void onTaskFailed(RequestTask task) {
        latch.countDown();
      }
    });

    // Submit 3 tasks
    for (int i = 0; i < 3; i++) {
      cli.submitTask(new HttpRequest("GET", httpbinUrl + "/get", null, null, null, 5000));
    }

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    var stats = cli.requestOverview.getStatistics();
    assertEquals(3, stats.getOrDefault(TaskStatus.SUCCEED, 0L));
  }

  @Test
  void testCliWithDifferentHttpMethods() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(2);

    cli.requestOverview.addListener(new TaskStatusListener() {
      @Override
      public void onTaskSubmitted(RequestTask task) {}

      @Override
      public void onTaskStarted(RequestTask task) {}

      @Override
      public void onTaskSucceed(RequestTask task) {
        latch.countDown();
      }

      @Override
      public void onTaskFailed(RequestTask task) {
        latch.countDown();
      }
    });

    // GET request
    cli.submitTask(new HttpRequest("GET", httpbinUrl + "/get", null, null, null, 5000));

    // POST request
    cli.submitTask(new HttpRequest("POST", httpbinUrl + "/post", null, null, "{\"test\":true}", 5000));

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    var allTasks = cli.requestOverview.getAllTasks();
    assertEquals(2, allTasks.size());
  }
}
