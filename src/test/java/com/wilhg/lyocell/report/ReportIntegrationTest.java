package com.wilhg.lyocell.report;

import com.wilhg.lyocell.engine.OutputConfig;
import com.wilhg.lyocell.engine.TestConfig;
import com.wilhg.lyocell.engine.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ReportIntegrationTest {

    @Test
    void testHtmlReportGenerationWithTimelineChart(@TempDir Path tempDir) throws Exception {
        // 1. Arrange: Configure the test to output an HTML report to a temporary directory
        Path scriptPath = Path.of("examples/basic-get.js");
        assertTrue(Files.exists(scriptPath), "Test script examples/basic-get.js should exist");

        OutputConfig htmlOutput = new OutputConfig("html", tempDir.toString());
        TestEngine engine = new TestEngine(List.of(htmlOutput));
        TestConfig config = new TestConfig(2, 5, null); // 2 VUs, 5 iterations to generate some data

        // 2. Act: Run the test engine
        engine.run(scriptPath, config);

        // 3. Assert: Verify the report was created and contains the timeline chart
        Optional<Path> reportFile = Files.list(tempDir)
                .filter(p -> p.toString().endsWith(".html"))
                .findFirst();
        
        assertTrue(reportFile.isPresent(), "HTML report file should have been created in the temp directory");

        String reportContent = Files.readString(reportFile.get());
        
        // Verify key sections to confirm correct rendering
        assertAll("HTML Report Content Verification",
            () -> assertTrue(reportContent.contains("<title>Lyocell Test Report</title>"), "Report should have the correct title"),
            () -> assertTrue(reportContent.contains("<h2>Request Volume Timeline (Reqs/Sec)</h2>"), "Report should contain the timeline chart title"),
            () -> assertTrue(reportContent.contains("class=\"timeline-chart\""), "Report should contain the timeline chart container"),
            () -> assertTrue(reportContent.contains("class=\"timeline-bar-wrapper\""), "Report should contain at least one timeline bar")
        );
    }
}
