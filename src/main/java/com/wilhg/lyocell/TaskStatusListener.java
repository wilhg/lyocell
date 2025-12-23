package com.wilhg.lyocell;

public interface TaskStatusListener {

  void onTaskSubmitted(RequestTask task);

  void onTaskStarted(RequestTask task);

  void onTaskSucceed(RequestTask task);

  void onTaskFailed(RequestTask task);
}
