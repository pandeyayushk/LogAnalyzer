package com.ayush.loganalyzer.core;

import com.ayush.loganalyzer.model.LogLevel;
import com.ayush.loganalyzer.report.AnalysisReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogAnalyzerTest {
    @TempDir
    Path directory;

    private final LogAnalyzer analyzer = new LogAnalyzer();

    @Test
    void aggregatesEverySeverityAndComputesPercentagesAndTimestampBounds() throws IOException {
        write("all.log", String.join("\n",
                line("2026-02-03 09:00:00", "TRACE", "A", "trace"),
                line("2026-02-03 08:00:00", "DEBUG", "A", "debug"),
                line("2026-02-03 10:00:00", "INFO", "B", "info"),
                line("2026-02-03 10:00:00", "WARN", "B", "warn"),
                line("2026-02-03 11:00:00", "ERROR", "A", "failure"),
                line("2026-02-03 12:00:00", "FATAL", "B", "fatal")));

        AnalysisReport report = analyzer.analyze(directory);

        assertEquals(6, report.totalEntries());
        assertEquals(1, report.discoveredFiles());
        assertEquals(1, report.processedFiles());
        assertEquals(0, report.skippedFiles());
        for (LogLevel level : LogLevel.values()) {
            assertEquals(1L, report.severityCounts().get(level));
            assertEquals(100.0 / 6.0, report.severityPercentages().get(level), 0.000001);
        }
        assertEquals(LocalDateTime.of(2026, 2, 3, 8, 0), report.earliestTimestamp().orElseThrow());
        assertEquals(LocalDateTime.of(2026, 2, 3, 12, 0), report.latestTimestamp().orElseThrow());
    }

    @Test
    void ranksLoggersAndFilesByCountWithDeterministicTieBreaks() throws IOException {
        write("z.log", String.join("\n",
                line("2026-01-01 00:00:00", "INFO", "zeta", "1"),
                line("2026-01-01 00:00:01", "INFO", "alpha", "2")));
        write("a.log", String.join("\n",
                line("2026-01-01 00:00:02", "INFO", "zeta", "3"),
                line("2026-01-01 00:00:03", "INFO", "alpha", "4")));

        AnalysisReport report = analyzer.analyze(directory);

        assertEquals(List.of("alpha", "zeta"), List.copyOf(report.loggerCounts().keySet()));
        assertEquals(List.of(2L, 2L), List.copyOf(report.loggerCounts().values()));
        assertEquals(List.of(directory.resolve("a.log"), directory.resolve("z.log")),
                List.copyOf(report.sourceFileCounts().keySet()));
    }

    @Test
    void aggregatesErrorsHourlyByLoggerAndSelectsEarliestPeakOnTie() throws IOException {
        write("errors.log", String.join("\n",
                line("2026-03-01 10:01:00", "ERROR", "Beta", "first"),
                line("2026-03-01 10:02:00", "FATAL", "Alpha", "second"),
                line("2026-03-01 11:01:00", "ERROR", "Alpha", "third"),
                line("2026-03-01 11:02:00", "INFO", "Alpha", "not an error")));

        AnalysisReport report = analyzer.analyze(directory);

        assertEquals(3, report.errorEntries());
        assertEquals(2L, report.errorCountsByLogger().get("Alpha"));
        assertEquals(1L, report.errorCountsByLogger().get("Beta"));
        assertEquals(2L, report.hourlyErrorCounts().get(LocalDateTime.of(2026, 3, 1, 10, 0)));
        assertEquals(1L, report.hourlyErrorCounts().get(LocalDateTime.of(2026, 3, 1, 11, 0)));
        assertEquals(LocalDateTime.of(2026, 3, 1, 10, 0), report.peakErrorHour().orElseThrow());
    }

    @Test
    void extractsExceptionClassOccurrencesFromMessageAndStackTrace() throws IOException {
        write("exceptions.log", String.join("\n",
                line("2026-04-01 08:00:00", "ERROR", "Worker", "java.lang.NullPointerException: missing"),
                "    at app.Service.call(Service.java:4)",
                "Caused by: java.io.IOException: disk issue",
                "com.example.ServiceException: failed twice com.example.ServiceException"));

        AnalysisReport report = analyzer.analyze(directory);

        assertEquals(2L, report.exceptionCounts().get("com.example.ServiceException"));
        assertEquals(1L, report.exceptionCounts().get("java.io.IOException"));
        assertEquals(1L, report.exceptionCounts().get("java.lang.NullPointerException"));
        assertFalse(report.exceptionCounts().containsKey("error"));
    }

    @Test
    void reportsMalformedLinesAndHandlesEmptyAndInvalidOnlyDirectories() throws IOException {
        write("empty.log", "");
        write("invalid.log", "bad one\nbad two");

        AnalysisReport report = analyzer.analyze(directory);

        assertEquals(0, report.totalEntries());
        assertEquals(2, report.discoveredFiles());
        assertEquals(2, report.processedFiles());
        assertEquals(0, report.skippedFiles());
        assertEquals(2, report.malformedLines());
        assertTrue(report.earliestTimestamp().isEmpty());
        assertTrue(report.latestTimestamp().isEmpty());
        assertTrue(report.peakErrorHour().isEmpty());
        assertEquals(0, report.errorEntries());
        assertTrue(report.hourlyErrorCounts().isEmpty());
        assertTrue(report.exceptionCounts().isEmpty());
        assertTrue(report.severityCounts().values().stream().allMatch(count -> count == 0));
        assertTrue(report.severityPercentages().values().stream().allMatch(value -> value == 0.0));
    }

    @Test
    void immutableReportMapsAndStreamedAggregationHandleManyEntries() throws IOException {
        StringBuilder fixture = new StringBuilder();
        for (int index = 0; index < 2_000; index++) {
            if (!fixture.isEmpty()) {
                fixture.append('\n');
            }
            fixture.append(line("2026-05-01 00:00:00", "DEBUG", "Bulk", "entry"));
        }
        write("bulk.log", fixture.toString());

        AnalysisReport report = analyzer.analyze(directory);

        assertEquals(2_000, report.totalEntries());
        assertEquals(2_000L, report.loggerCounts().get("Bulk"));
        assertThrows(UnsupportedOperationException.class,
                () -> report.loggerCounts().put("other", 1L));
        assertThrows(UnsupportedOperationException.class,
                () -> report.severityCounts().put(LogLevel.ERROR, 99L));
    }

    @Test
    void noErrorsProducesNoPeakHour() throws IOException {
        write("normal.log", line("2026-06-01 12:00:00", "INFO", "App", "running"));

        AnalysisReport report = analyzer.analyze(directory);

        assertEquals(0, report.errorEntries());
        assertTrue(report.errorCountsByLogger().isEmpty());
        assertTrue(report.hourlyErrorCounts().isEmpty());
        assertTrue(report.peakErrorHour().isEmpty());
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(directory.resolve(name), content, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String line(String timestamp, String level, String logger, String message) {
        return timestamp + " " + level + " " + logger + " - " + message;
    }
}
