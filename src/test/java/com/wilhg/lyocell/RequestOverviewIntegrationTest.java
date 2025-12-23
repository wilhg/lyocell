package com.wilhg.lyocell;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RequestOverviewIntegrationTest {
  @Container
  private static final GenericContainer<?> httpbin = new GenericContainer<>("sharat87/httpbun")
      .withExposedPorts(80);

  private RequestOverview requestOverview;
  private String httpbinUrl;

  @BeforeEach
  void setUp() {
    requestOverview = new RequestOverview();
    httpbinUrl = "http://" + httpbin.getHost() + ":" + httpbin.getMappedPort(80);
  }

  @AfterEach
  void tearDown() {
    requestOverview.shutdown();
  }

  @Test
  void testEightTasksWithHttpBin() throws InterruptedException {
    CountDownLatch completionLatch = new CountDownLatch(8);
    List<String> taskIds = new ArrayList<>();

    // Add listener to track completion
    requestOverview.addListener(new TaskStatusListener() {
      @Override
      public void onTaskSubmitted(RequestTask task) {
        System.out.println("Task submitted: " + task.getId());
      }

      @Override
      public void onTaskStarted(RequestTask task) {
        System.out.println("Task started: " + task.getId());
      }

      @Override
      public void onTaskSucceed(RequestTask task) {
        System.out.println("Task succeeded: " + task.getId() + " - HTTP Status: " + task.getHttpStatus());
        completionLatch.countDown();
      }

      @Override
      public void onTaskFailed(RequestTask task) {
        System.err.println("Task failed: " + task.getId() + " - Error: " + task.getError());
        completionLatch.countDown();
      }
    });

    // Submit 8 tasks to httpbin
    for (int i = 0; i < 8; i++) {
      HttpRequest request = new HttpRequest(
          "GET",
          httpbinUrl + "/delay/" + (i % 3), // delays: 0, 1, 2 seconds
          null,
          Map.of("User-Agent", "Lyocell-Test"),
          null,
          5000
      );
      String taskId = requestOverview.submitTask(request);
      taskIds.add(taskId);
    }

    // Wait for all tasks to complete (max 15 seconds)
    boolean completed = completionLatch.await(15, TimeUnit.SECONDS);
    assertTrue(completed, "All tasks should complete within 15 seconds");

    // Verify all tasks
    for (String taskId : taskIds) {
      RequestTask task = requestOverview.getTask(taskId);
      assertNotNull(task, "Task should exist: " + taskId);
      assertTrue(task.getStatus() == TaskStatus.SUCCEED || task.getStatus() == TaskStatus.FAILED,
          "Task should be completed: " + taskId);

      if (task.getStatus() == TaskStatus.SUCCEED) {
        assertEquals(200, task.getHttpStatus(), "HTTP status should be 200 for task: " + taskId);
        assertNotNull(task.getResponseBody(), "Response body should not be null for task: " + taskId);
        assertNotNull(task.getStartedAt(), "Started time should be set for task: " + taskId);
        assertNotNull(task.getCompletedAt(), "Completed time should be set for task: " + taskId);
      }
    }

    // Verify statistics
    Map<TaskStatus, Long> stats = requestOverview.getStatistics();
    System.out.println("Statistics: " + stats);
    long totalCompleted = stats.getOrDefault(TaskStatus.SUCCEED, 0L) + stats.getOrDefault(TaskStatus.FAILED, 0L);
    assertEquals(8, totalCompleted, "Total completed tasks should be 8");

    // Verify no tasks are still pending
    assertEquals(0, requestOverview.getTasksByStatus(TaskStatus.PENDING).size(),
        "No tasks should be in PENDING state");
    assertEquals(0, requestOverview.getTasksByStatus(TaskStatus.AWAITING).size(),
        "No tasks should be in AWAITING state");
  }

  @Test
  void testTwoTasksInParallel() throws InterruptedException {
    CountDownLatch startLatch = new CountDownLatch(2);
    CountDownLatch completionLatch = new CountDownLatch(2);

    requestOverview.addListener(new TaskStatusListener() {
      @Override
      public void onTaskSubmitted(RequestTask task) {}

      @Override
      public void onTaskStarted(RequestTask task) {
        startLatch.countDown();
      }

      @Override
      public void onTaskSucceed(RequestTask task) {
        completionLatch.countDown();
      }

      @Override
      public void onTaskFailed(RequestTask task) {
        completionLatch.countDown();
      }
    });

    // Submit 2 tasks
    HttpRequest request1 = new HttpRequest(
        "GET",
        httpbinUrl + "/get",
        Map.of("test", "value1"),
        Map.of("User-Agent", "Lyocell-Test"),
        null,
        5000
    );

    HttpRequest request2 = new HttpRequest(
        "GET",
        httpbinUrl + "/get",
        Map.of("test", "value2"),
        Map.of("User-Agent", "Lyocell-Test"),
        null,
        5000
    );

    String taskId1 = requestOverview.submitTask(request1);
    String taskId2 = requestOverview.submitTask(request2);

    // Verify both tasks start (running in parallel)
    boolean bothStarted = startLatch.await(5, TimeUnit.SECONDS);
    assertTrue(bothStarted, "Both tasks should start");

    // Wait for completion
    boolean completed = completionLatch.await(10, TimeUnit.SECONDS);
    assertTrue(completed, "Both tasks should complete within 10 seconds");

    // Verify results
    RequestTask task1 = requestOverview.getTask(taskId1);
    RequestTask task2 = requestOverview.getTask(taskId2);

    assertEquals(TaskStatus.SUCCEED, task1.getStatus());
    assertEquals(TaskStatus.SUCCEED, task2.getStatus());
    assertEquals(200, task1.getHttpStatus());
    assertEquals(200, task2.getHttpStatus());

    System.out.println("Task 1 response length: " + task1.getResponseBody().length());
    System.out.println("Task 2 response length: " + task2.getResponseBody().length());
  }

  @Test
  void testPostRequestWithBody() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    requestOverview.addListener(new TaskStatusListener() {
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

    HttpRequest request = new HttpRequest(
        "POST",
        httpbinUrl + "/post",
        null,
        Map.of("Content-Type", "application/json"),
        "{\"name\":\"test\",\"value\":123}",
        5000
    );

    String taskId = requestOverview.submitTask(request);
    assertTrue(latch.await(10, TimeUnit.SECONDS));

    RequestTask task = requestOverview.getTask(taskId);
    assertEquals(TaskStatus.SUCCEED, task.getStatus());
    assertEquals(200, task.getHttpStatus());
    assertTrue(task.getResponseBody().contains("test"));
  }

  @Test
  void testFailedRequest() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    requestOverview.addListener(new TaskStatusListener() {
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

    // Request with invalid URL
    HttpRequest request = new HttpRequest(
        "GET",
        "http://invalid-host-that-does-not-exist.test/get",
        null,
        null,
        null,
        2000
    );

    String taskId = requestOverview.submitTask(request);
    assertTrue(latch.await(10, TimeUnit.SECONDS));

    RequestTask task = requestOverview.getTask(taskId);
    assertEquals(TaskStatus.FAILED, task.getStatus());
    assertNotNull(task.getError());
    assertNull(task.getHttpStatus());
  }

  @Test
  void testGetAllTasks() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    requestOverview.addListener(new TaskStatusListener() {
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
      HttpRequest request = new HttpRequest(
          "GET",
          httpbinUrl + "/get",
          null,
          null,
          null,
          5000
      );
      requestOverview.submitTask(request);
    }

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    var allTasks = requestOverview.getAllTasks();
    assertEquals(3, allTasks.size());
  }

  @Test
  void testGetLatestTask() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    requestOverview.addListener(new TaskStatusListener() {
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

    List<String> taskIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      HttpRequest request = new HttpRequest(
          "GET",
          httpbinUrl + "/get",
          null,
          null,
          null,
          5000
      );
      String taskId = requestOverview.submitTask(request);
      taskIds.add(taskId);
      Thread.sleep(100); // Ensure different creation times
    }

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    RequestTask latestTask = requestOverview.getLatestTask();
    assertNotNull(latestTask);
    assertEquals(taskIds.get(2), latestTask.getId());
  }

  @Test
  void testGetTasksChronologically() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    requestOverview.addListener(new TaskStatusListener() {
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

    List<String> taskIds = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      HttpRequest request = new HttpRequest(
          "GET",
          httpbinUrl + "/get",
          null,
          null,
          null,
          5000
      );
      String taskId = requestOverview.submitTask(request);
      taskIds.add(taskId);
      Thread.sleep(100);
    }

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    List<RequestTask> chronologicalTasks = requestOverview.getTasksChronologically();
    assertEquals(3, chronologicalTasks.size());
    assertEquals(taskIds.get(0), chronologicalTasks.get(0).getId());
    assertEquals(taskIds.get(1), chronologicalTasks.get(1).getId());
    assertEquals(taskIds.get(2), chronologicalTasks.get(2).getId());
  }

  @Test
  void testRemoveListener() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    int[] callCount = {0};

    TaskStatusListener listener = new TaskStatusListener() {
      @Override
      public void onTaskSubmitted(RequestTask task) {
        callCount[0]++;
      }

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
    };

    requestOverview.addListener(listener);

    // First task - listener should be called
    HttpRequest request1 = new HttpRequest(
        "GET",
        httpbinUrl + "/get",
        null,
        null,
        null,
        5000
    );
    requestOverview.submitTask(request1);

    assertTrue(latch.await(10, TimeUnit.SECONDS));
    assertEquals(1, callCount[0]);

    // Remove listener
    requestOverview.removeListener(listener);

    // Second task - listener should NOT be called
    HttpRequest request2 = new HttpRequest(
        "GET",
        httpbinUrl + "/get",
        null,
        null,
        null,
        5000
    );
    requestOverview.submitTask(request2);

    Thread.sleep(2000);
    assertEquals(1, callCount[0]); // Should still be 1
  }

  @Test
  void testDifferentHttpMethods() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(3);

    requestOverview.addListener(new TaskStatusListener() {
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

    // GET
    HttpRequest getRequest = new HttpRequest(
        "GET",
        httpbinUrl + "/get",
        null,
        null,
        null,
        5000
    );
    String getId = requestOverview.submitTask(getRequest);

    // POST
    HttpRequest postRequest = new HttpRequest(
        "POST",
        httpbinUrl + "/post",
        null,
        null,
        "test data",
        5000
    );
    String postId = requestOverview.submitTask(postRequest);

    // PUT
    HttpRequest putRequest = new HttpRequest(
        "PUT",
        httpbinUrl + "/put",
        null,
        null,
        "test data",
        5000
    );
    String putId = requestOverview.submitTask(putRequest);

    assertTrue(latch.await(10, TimeUnit.SECONDS));

    assertEquals(TaskStatus.SUCCEED, requestOverview.getTask(getId).getStatus());
    assertEquals(TaskStatus.SUCCEED, requestOverview.getTask(postId).getStatus());
    assertEquals(TaskStatus.SUCCEED, requestOverview.getTask(putId).getStatus());
  }

  @Test
  void testTaskWithQueryParamsAndHeaders() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    requestOverview.addListener(new TaskStatusListener() {
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

    HttpRequest request = new HttpRequest(
        "GET",
        httpbinUrl + "/get",
        Map.of("param1", "value1", "param2", "value2"),
        Map.of("X-Custom-Header", "test-value", "User-Agent", "Lyocell-Test"),
        null,
        5000
    );

    String taskId = requestOverview.submitTask(request);
    assertTrue(latch.await(10, TimeUnit.SECONDS));

    RequestTask task = requestOverview.getTask(taskId);
    assertEquals(TaskStatus.SUCCEED, task.getStatus());
    assertTrue(task.getResponseBody().contains("param1"));
    assertTrue(task.getResponseBody().contains("value1"));
  }

  @Test
  void testTaskTiming() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    requestOverview.addListener(new TaskStatusListener() {
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

    HttpRequest request = new HttpRequest(
        "GET",
        httpbinUrl + "/get",
        null,
        null,
        null,
        5000
    );

    String taskId = requestOverview.submitTask(request);
    assertTrue(latch.await(10, TimeUnit.SECONDS));

    RequestTask task = requestOverview.getTask(taskId);
    assertNotNull(task.getCreatedAt());
    assertNotNull(task.getStartedAt());
    assertNotNull(task.getCompletedAt());
    assertTrue(task.getCreatedAt().isBefore(task.getStartedAt()) ||
        task.getCreatedAt().isEqual(task.getStartedAt()));
    assertTrue(task.getStartedAt().isBefore(task.getCompletedAt()) ||
        task.getStartedAt().isEqual(task.getCompletedAt()));
  }

  @Test
  void testStatisticsWithMixedResults() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(4);

    requestOverview.addListener(new TaskStatusListener() {
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

    // 2 successful requests
    for (int i = 0; i < 2; i++) {
      HttpRequest request = new HttpRequest(
          "GET",
          httpbinUrl + "/get",
          null,
          null,
          null,
          5000
      );
      requestOverview.submitTask(request);
    }

    // 2 failed requests
    for (int i = 0; i < 2; i++) {
      HttpRequest request = new HttpRequest(
          "GET",
          "http://invalid-host-" + i + ".test/get",
          null,
          null,
          null,
          2000
      );
      requestOverview.submitTask(request);
    }

    assertTrue(latch.await(15, TimeUnit.SECONDS));

    Map<TaskStatus, Long> stats = requestOverview.getStatistics();
    assertEquals(2, stats.getOrDefault(TaskStatus.SUCCEED, 0L));
    assertEquals(2, stats.getOrDefault(TaskStatus.FAILED, 0L));
  }
}
