package com.wilhg.lyocell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliArgumentParserTest {

  @Test
  void testSimpleGetRequest() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get"
    });

    assertEquals("GET", parser.getMethod());
    assertEquals("https://httpbin.org/get", parser.getUrl());
    assertTrue(parser.getHeaders().isEmpty());
    assertTrue(parser.getQueryParams().isEmpty());
    assertNull(parser.getBody());
  }

  @Test
  void testGetRequestWithDefaultMethod() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "https://httpbin.org/get"
    });

    assertEquals("GET", parser.getMethod());
    assertEquals("https://httpbin.org/get", parser.getUrl());
  }

  @Test
  void testGetRequestWithQueryParams() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "https://httpbin.org/get", "name==John", "age==30"
    });

    assertEquals("GET", parser.getMethod());
    assertEquals(2, parser.getQueryParams().size());
    assertEquals("John", parser.getQueryParams().get("name"));
    assertEquals("30", parser.getQueryParams().get("age"));
  }

  @Test
  void testPostRequestWithJsonBody() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "POST", "https://httpbin.org/post", "name=John", "age:=30"
    });

    assertEquals("POST", parser.getMethod());
    assertEquals("{\"name\":\"John\",\"age\":30}", parser.getBody());
  }

  @Test
  void testRequestWithHeaders() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get",
        "User-Agent:Lyocell", "Authorization:Bearer token"
    });

    assertEquals(2, parser.getHeaders().size());
    assertEquals("Lyocell", parser.getHeaders().get("User-Agent"));
    assertEquals("Bearer token", parser.getHeaders().get("Authorization"));
  }

  @Test
  void testTimeoutWithEquals() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "--timeout=3000"
    });

    assertEquals(3000, parser.getTimeout());
  }

  @Test
  void testTimeoutWithSpace() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "--timeout", "3000"
    });

    assertEquals(3000, parser.getTimeout());
  }

  @Test
  void testTimeoutShortFormWithEquals() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "-to=2000"
    });

    assertEquals(2000, parser.getTimeout());
  }

  @Test
  void testTimeoutShortFormWithSpace() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "-to", "2000"
    });

    assertEquals(2000, parser.getTimeout());
  }

  @Test
  void testComplexRequest() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "POST",
        "https://httpbin.org/post",
        "name=John",
        "age:=30",
        "active:=true",
        "search==term",
        "User-Agent:Lyocell",
        "--timeout=5000"
    });

    assertEquals("POST", parser.getMethod());
    assertEquals("https://httpbin.org/post", parser.getUrl());
    assertEquals("{\"name\":\"John\",\"age\":30,\"active\":true}", parser.getBody());
    assertEquals("term", parser.getQueryParams().get("search"));
    assertEquals("Lyocell", parser.getHeaders().get("User-Agent"));
    assertEquals(5000, parser.getTimeout());
  }

  @Test
  void testEmptyArgsThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> {
      CliArgumentParser.parse(new String[]{});
    });
  }

  @Test
  void testMethodOnlyThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> {
      CliArgumentParser.parse(new String[]{"GET"});
    });
  }

  @Test
  void testToHttpRequest() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "POST", "https://httpbin.org/post", "name=Test"
    });

    HttpRequest request = parser.toHttpRequest();

    assertEquals("POST", request.method());
    assertEquals("https://httpbin.org/post", request.url());
    assertEquals("{\"name\":\"Test\"}", request.body());
  }

  @Test
  void testConcurrencyOptionWithEquals() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "-c=10"
    });

    assertEquals(10, parser.getConcurrency());
  }

  @Test
  void testConcurrencyOptionWithSpace() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "-c", "10"
    });

    assertEquals(10, parser.getConcurrency());
  }

  @Test
  void testConcurrencyOptionLongForm() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "--concurrency=20"
    });

    assertEquals(20, parser.getConcurrency());
  }

  @Test
  void testConcurrencyOptionLongFormWithSpace() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "--concurrency", "20"
    });

    assertEquals(20, parser.getConcurrency());
  }

  @Test
  void testRequestsOptionWithEquals() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "-n=100"
    });

    assertEquals(100, parser.getRequests());
  }

  @Test
  void testRequestsOptionWithSpace() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "-n", "100"
    });

    assertEquals(100, parser.getRequests());
  }

  @Test
  void testRequestsOptionLongForm() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "--requests=50"
    });

    assertEquals(50, parser.getRequests());
  }

  @Test
  void testRequestsOptionLongFormWithSpace() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "GET", "https://httpbin.org/get", "--requests", "50"
    });

    assertEquals(50, parser.getRequests());
  }

  @Test
  void testLoadTestingWithAllOptions() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "POST",
        "https://httpbin.org/post",
        "name=Test",
        "age:=25",
        "-n=100",
        "-c=10",
        "--timeout=3000"
    });

    assertEquals("POST", parser.getMethod());
    assertEquals("https://httpbin.org/post", parser.getUrl());
    assertEquals("{\"name\":\"Test\",\"age\":25}", parser.getBody());
    assertEquals(100, parser.getRequests());
    assertEquals(10, parser.getConcurrency());
    assertEquals(3000, parser.getTimeout());
  }

  @Test
  void testLoadTestingWithSpaceFormat() {
    CliArgumentParser parser = CliArgumentParser.parse(new String[]{
        "POST",
        "https://httpbin.org/post",
        "name=Test",
        "age:=25",
        "-n", "100",
        "-c", "10",
        "-to", "3000"
    });

    assertEquals("POST", parser.getMethod());
    assertEquals("https://httpbin.org/post", parser.getUrl());
    assertEquals("{\"name\":\"Test\",\"age\":25}", parser.getBody());
    assertEquals(100, parser.getRequests());
    assertEquals(10, parser.getConcurrency());
    assertEquals(3000, parser.getTimeout());
  }
}
