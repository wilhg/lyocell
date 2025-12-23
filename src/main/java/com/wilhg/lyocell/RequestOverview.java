package com.wilhg.lyocell;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RequestOverview {

  private static final int DEFAULT_POOL_SIZE = 100;

  private final ConcurrentHashMap<String, RequestTask> tasks;
  private final LinkedBlockingQueue<RequestTask> taskQueue;
  private final ExecutorService virtualThreadPool;
  private final ConcurrentHttpClient httpClient;
  private final CopyOnWriteArrayList<TaskStatusListener> listeners;
  private final AtomicLong taskIdCounter;
  private final ExecutorService queueProcessor;

  public RequestOverview() {
    this.tasks = new ConcurrentHashMap<>();
    this.taskQueue = new LinkedBlockingQueue<>();
    // Use named virtual threads for better monitoring and debugging
    this.virtualThreadPool = Executors.newFixedThreadPool(
        DEFAULT_POOL_SIZE,
        Thread.ofVirtual().name("lyocell-worker-", 0).factory()
    );
    this.httpClient = new ConcurrentHttpClient();
    this.listeners = new CopyOnWriteArrayList<>();
    this.taskIdCounter = new AtomicLong(0);
    this.queueProcessor = Executors.newSingleThreadExecutor(
        Thread.ofVirtual().name("lyocell-queue-processor").factory()
    );

    startQueueProcessor();
  }

  private void startQueueProcessor() {
    queueProcessor.submit(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          RequestTask task = taskQueue.take();
          virtualThreadPool.submit(() -> processTask(task));
        } catch (InterruptedException _) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
  }

  public String submitTask(HttpRequest request) {
    String taskId = String.valueOf(taskIdCounter.incrementAndGet());
    RequestTask task = new RequestTask(taskId, request);
    tasks.put(taskId, task);
    notifyListeners(listener -> listener.onTaskSubmitted(task));

    try {
      taskQueue.put(task);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      task.updateStatus(TaskStatus.FAILED);
      task.setError(e);
      notifyListeners(listener -> listener.onTaskFailed(task));
    }

    return taskId;
  }

  private void processTask(RequestTask task) {
    // Create request context and bind it to ScopedValue
    RequestContext context = new RequestContext(
        task.getId(),
        UUID.randomUUID().toString(),
        System.currentTimeMillis()
    );

    // Run with ScopedValue bound to the context
    ScopedValue.where(RequestContext.CURRENT, context).run(() -> {
      task.updateStatus(TaskStatus.PENDING);
      notifyListeners(listener -> listener.onTaskStarted(task));

      // Use StructuredTaskScope for the HTTP request
      try (var scope = StructuredTaskScope.open()) {
        var httpRequestFuture = scope.fork(() -> {
          // ScopedValue is automatically inherited by forked tasks
          return httpClient.query(task.getRequest()).join();
        });

        scope.join();

        var response = httpRequestFuture.get();
        task.setResult(response.statusCode(), response.body());
        task.updateStatus(TaskStatus.SUCCEED);
        notifyListeners(listener -> listener.onTaskSucceed(task));
      } catch (Exception e) {
        task.setError(e);
        task.updateStatus(TaskStatus.FAILED);
        notifyListeners(listener -> listener.onTaskFailed(task));
      }
    });
  }

  private void notifyListeners(Consumer<TaskStatusListener> action) {
    for (TaskStatusListener listener : listeners) {
      try {
        action.accept(listener);
      } catch (Exception _) {
        // Log but don't propagate listener exceptions (using unnamed variable)
      }
    }
  }

  public void addListener(TaskStatusListener listener) {
    listeners.add(listener);
  }

  public void removeListener(TaskStatusListener listener) {
    listeners.remove(listener);
  }

  public RequestTask getTask(String id) {
    return tasks.get(id);
  }

  public Collection<RequestTask> getAllTasks() {
    return tasks.values();
  }

  public List<RequestTask> getTasksByStatus(TaskStatus status) {
    return tasks.values().stream()
        .filter(task -> task.getStatus() == status)
        .collect(Collectors.toList());
  }

  public Map<TaskStatus, Long> getStatistics() {
    return tasks.values().stream()
        .collect(Collectors.groupingBy(RequestTask::getStatus, Collectors.counting()));
  }

  // Using Sequenced Collections API - get the most recent task
  public RequestTask getLatestTask() {
    return tasks.values().stream()
        .max(Comparator.comparing(RequestTask::getCreatedAt))
        .orElse(null);
  }

  // Get tasks in chronological order
  public List<RequestTask> getTasksChronologically() {
    return tasks.values().stream()
        .sorted(Comparator.comparing(RequestTask::getCreatedAt))
        .toList();
  }

  public void shutdown() {
    queueProcessor.shutdownNow();
    virtualThreadPool.shutdown();
  }
}
