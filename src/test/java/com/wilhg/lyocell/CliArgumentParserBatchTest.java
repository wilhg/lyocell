package com.wilhg.lyocell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliArgumentParserBatchTest {

  @Test
  void testParseBatchModeYamlFile() {
    String[] args = {"requests.yaml"};
    CliArgumentParser parser = CliArgumentParser.parse(args);

    assertTrue(parser.isBatchMode());
    assertEquals("requests.yaml", parser.getYamlFile());
  }

  @Test
  void testParseBatchModeYmlFile() {
    String[] args = {"batch-requests.yml"};
    CliArgumentParser parser = CliArgumentParser.parse(args);

    assertTrue(parser.isBatchMode());
    assertEquals("batch-requests.yml", parser.getYamlFile());
  }

  @Test
  void testParseBatchModeWithPath() {
    String[] args = {"/path/to/requests.yaml"};
    CliArgumentParser parser = CliArgumentParser.parse(args);

    assertTrue(parser.isBatchMode());
    assertEquals("/path/to/requests.yaml", parser.getYamlFile());
  }

  @Test
  void testParseBatchModeWithRelativePath() {
    String[] args = {"./config/requests.yaml"};
    CliArgumentParser parser = CliArgumentParser.parse(args);

    assertTrue(parser.isBatchMode());
    assertEquals("./config/requests.yaml", parser.getYamlFile());
  }

  @Test
  void testParseNonBatchMode() {
    String[] args = {"GET", "https://httpbin.org/get"};
    CliArgumentParser parser = CliArgumentParser.parse(args);

    assertFalse(parser.isBatchMode());
    assertNull(parser.getYamlFile());
    assertEquals("GET", parser.getMethod());
    assertEquals("https://httpbin.org/get", parser.getUrl());
  }

  @Test
  void testParseHttpUrlNotBatchMode() {
    String[] args = {"https://httpbin.org/get"};
    CliArgumentParser parser = CliArgumentParser.parse(args);

    assertFalse(parser.isBatchMode());
    assertNull(parser.getYamlFile());
    assertEquals("GET", parser.getMethod());
    assertEquals("https://httpbin.org/get", parser.getUrl());
  }
}
