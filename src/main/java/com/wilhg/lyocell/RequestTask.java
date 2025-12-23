package com.wilhg.lyocell;

import java.time.LocalDateTime;

public class RequestTask {

  private final String id;
  private final HttpRequest request;
  private final LocalDateTime createdAt;
  private volatile TaskStatus status;
  private volatile Integer httpStatus;
  private volatile String responseBody;
  private volatile Throwable error;
  private volatile LocalDateTime startedAt;
  private volatile LocalDateTime completedAt;

  public RequestTask(String id, HttpRequest request) {
    this.id = id;
    this.request = request;
    this.createdAt = LocalDateTime.now();
    this.status = TaskStatus.AWAITING;
  }

  public synchronized void updateStatus(TaskStatus newStatus) {
    this.status = newStatus;
    if (newStatus == TaskStatus.PENDING) {
      this.startedAt = LocalDateTime.now();
    } else if (newStatus == TaskStatus.SUCCEED || newStatus == TaskStatus.FAILED) {
      this.completedAt = LocalDateTime.now();
    }
  }

  public synchronized void setResult(int httpStatus, String responseBody) {
    this.httpStatus = httpStatus;
    this.responseBody = responseBody;
  }

  public synchronized void setError(Throwable error) {
    this.error = error;
  }

  public String getId() {
    return id;
  }

  public HttpRequest getRequest() {
    return request;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public Integer getHttpStatus() {
    return httpStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public Throwable getError() {
    return error;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }
}
