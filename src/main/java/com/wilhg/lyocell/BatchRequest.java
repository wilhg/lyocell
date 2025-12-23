package com.wilhg.lyocell;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class BatchRequest {

  @JsonProperty("requests")
  private List<RequestDefinition> requests;

  @JsonProperty("defaults")
  private RequestDefaults defaults;

  public List<RequestDefinition> getRequests() {
    return requests;
  }

  public void setRequests(List<RequestDefinition> requests) {
    this.requests = requests;
  }

  public RequestDefaults getDefaults() {
    return defaults;
  }

  public void setDefaults(RequestDefaults defaults) {
    this.defaults = defaults;
  }

  public static class RequestDefinition {

    @JsonProperty("name")
    private String name;

    @JsonProperty("method")
    private String method;

    @JsonProperty("url")
    private String url;

    @JsonProperty("headers")
    private Map<String, String> headers;

    @JsonProperty("queryParams")
    private Map<String, String> queryParams;

    @JsonProperty("body")
    private Object body;

    @JsonProperty("timeout")
    private Integer timeout;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }

    public void setHeaders(Map<String, String> headers) {
      this.headers = headers;
    }

    public Map<String, String> getQueryParams() {
      return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
      this.queryParams = queryParams;
    }

    public Object getBody() {
      return body;
    }

    public void setBody(Object body) {
      this.body = body;
    }

    public Integer getTimeout() {
      return timeout;
    }

    public void setTimeout(Integer timeout) {
      this.timeout = timeout;
    }
  }

  public static class RequestDefaults {

    @JsonProperty("timeout")
    private Integer timeout;

    @JsonProperty("headers")
    private Map<String, String> headers;

    public Integer getTimeout() {
      return timeout;
    }

    public void setTimeout(Integer timeout) {
      this.timeout = timeout;
    }

    public Map<String, String> getHeaders() {
      return headers;
    }

    public void setHeaders(Map<String, String> headers) {
      this.headers = headers;
    }
  }
}
