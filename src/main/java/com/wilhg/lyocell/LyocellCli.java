package com.wilhg.lyocell;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LyocellCli {

  final RequestOverview requestOverview; // Package-private for testing
  private final Map<String, TaskInfo> taskInfoMap;
  private final AtomicInteger totalSubmitted;
  private final AtomicInteger totalSucceeded;
  private final AtomicInteger totalFailed;

  public LyocellCli() {
    this.requestOverview = new RequestOverview();
    this.taskInfoMap = new ConcurrentHashMap<>();
    this.totalSubmitted = new AtomicInteger(0);
    this.totalSucceeded = new AtomicInteger(0);
    this.totalFailed = new AtomicInteger(0);

    setupListeners();
  }

  private void setupListeners() {
    requestOverview.addListener(new TaskStatusListener() {
      @Override
      public void onTaskSubmitted(RequestTask task) {
        totalSubmitted.incrementAndGet();
        taskInfoMap.put(task.getId(), new TaskInfo(task.getId(), "AWAITING", null, null));
      }

      @Override
      public void onTaskStarted(RequestTask task) {
        TaskInfo info = taskInfoMap.get(task.getId());
        if (info != null) {
          info.status = "PENDING";
          info.startedAt = task.getStartedAt();
        }
      }

      @Override
      public void onTaskSucceed(RequestTask task) {
        totalSucceeded.incrementAndGet();
        TaskInfo info = taskInfoMap.get(task.getId());
        if (info != null) {
          info.status = "SUCCESS";
          info.httpStatus = task.getHttpStatus();
          info.completedAt = task.getCompletedAt();
        }
      }

      @Override
      public void onTaskFailed(RequestTask task) {
        totalFailed.incrementAndGet();
        TaskInfo info = taskInfoMap.get(task.getId());
        if (info != null) {
          info.status = "FAILED";
          info.error = task.getError() != null ? task.getError().getMessage() : "Unknown error";
          info.completedAt = task.getCompletedAt();
        }
      }
    });
  }

  public void submitTask(HttpRequest request) {
    requestOverview.submitTask(request);
  }

  public void runUI() {
    try (Terminal terminal = TerminalBuilder.builder()
        .system(true)
        .nativeSignals(true)
        .signalHandler(Terminal.SignalHandler.SIG_IGN)
        .build()) {
      AtomicBoolean running = new AtomicBoolean(true);

      // Handle Ctrl+C
      terminal.handle(Terminal.Signal.INT, signal -> running.set(false));

      while (running.get()) {
        clearScreen(terminal);
        renderUI(terminal);

        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          running.set(false);
          Thread.currentThread().interrupt();
        }
      }
    } catch (IOException e) {
      System.err.println("Failed to create terminal: " + e.getMessage());
    }
  }

  private void clearScreen(Terminal terminal) {
    terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
    terminal.flush();
  }

  private void renderUI(Terminal terminal) {
    AttributedStyle headerStyle = AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.CYAN);
    AttributedStyle successStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
    AttributedStyle failStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);
    AttributedStyle pendingStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW);

    // Header
    terminal.writer().println(new AttributedString("╔═══════════════════════════════════════════════════════════╗", headerStyle).toAnsi());
    terminal.writer().println(new AttributedString("║               Lyocell - HTTP Client Monitor               ║", headerStyle).toAnsi());
    terminal.writer().println(new AttributedString("╚═══════════════════════════════════════════════════════════╝", headerStyle).toAnsi());
    terminal.writer().println();

    // Statistics
    terminal.writer().println("📊 Statistics:");
    int inProgress = totalSubmitted.get() - totalSucceeded.get() - totalFailed.get();
    terminal.writer().println(String.format("   Total: %d | %s Success: %d | %s Failed: %d | %s In Progress: %d",
        totalSubmitted.get(),
        "✓", totalSucceeded.get(),
        "✗", totalFailed.get(),
        "⏳", inProgress
    ));
    terminal.writer().println();

    // Recent tasks
    terminal.writer().println("📝 Recent Tasks:");
    List<RequestTask> recentTasks = requestOverview.getTasksChronologically();
    int displayCount = Math.min(15, recentTasks.size());

    int startIndex = Math.max(0, recentTasks.size() - displayCount);
    for (int i = startIndex; i < recentTasks.size(); i++) {
      RequestTask task = recentTasks.get(i);
      TaskInfo info = taskInfoMap.get(task.getId());
      if (info != null) {
        String statusIcon = getStatusIcon(info.status);
        AttributedStyle style = getStyle(info.status, successStyle, failStyle, pendingStyle);
        String duration = getDuration(task);

        String line = String.format("   %s Task #%s - %-10s - %s %s - %s",
            statusIcon,
            task.getId(),
            info.status,
            task.getRequest().method(),
            shortenUrl(task.getRequest().url()),
            duration
        );

        terminal.writer().println(new AttributedString(line, style).toAnsi());
      }
    }

    terminal.writer().println();
    terminal.writer().println("Press Ctrl+C to exit");
    terminal.flush();
  }

  private AttributedStyle getStyle(String status, AttributedStyle success, AttributedStyle fail, AttributedStyle pending) {
    return switch (status) {
      case "SUCCESS" -> success;
      case "FAILED" -> fail;
      case "PENDING", "AWAITING" -> pending;
      default -> AttributedStyle.DEFAULT;
    };
  }

  private String getStatusIcon(String status) {
    return switch (status) {
      case "SUCCESS" -> "✓";
      case "FAILED" -> "✗";
      case "PENDING" -> "⏳";
      case "AWAITING" -> "⏸";
      default -> "?";
    };
  }


  private String getDuration(RequestTask task) {
    if (task.getCompletedAt() != null && task.getStartedAt() != null) {
      Duration duration = Duration.between(task.getStartedAt(), task.getCompletedAt());
      return String.format("%dms", duration.toMillis());
    } else if (task.getStartedAt() != null) {
      Duration duration = Duration.between(task.getStartedAt(), LocalDateTime.now());
      return String.format("%dms (ongoing)", duration.toMillis());
    }
    return "pending";
  }

  private String shortenUrl(String url) {
    if (url.length() > 40) {
      return url.substring(0, 37) + "...";
    }
    return url;
  }

  public void shutdown() {
    requestOverview.shutdown();
  }

  private static class TaskInfo {
    String taskId;
    String status;
    Integer httpStatus;
    String error;
    LocalDateTime startedAt;
    LocalDateTime completedAt;

    TaskInfo(String taskId, String status, Integer httpStatus, String error) {
      this.taskId = taskId;
      this.status = status;
      this.httpStatus = httpStatus;
      this.error = error;
    }
  }
}
