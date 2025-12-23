package com.wilhg.lyocell;

import java.util.Map;

public record HttpRequest(
    String method,
    String url,
    Map<String, String> queryParams,
    Map<String, String> headers,
    String body,
    int timeout
) {

}
