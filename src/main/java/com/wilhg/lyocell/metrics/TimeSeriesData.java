package com.wilhg.lyocell.metrics;

public record TimeSeriesData(long timestamp, long successfulRequests, long failedRequests) {}
