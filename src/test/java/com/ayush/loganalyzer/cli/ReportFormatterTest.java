package com.ayush.loganalyzer.cli;

import com.ayush.loganalyzer.core.DirectoryReadResult;
import com.ayush.loganalyzer.core.FilterResult;
import com.ayush.loganalyzer.model.LogEntry;
import com.ayush.loganalyzer.model.LogLevel;
import com.ayush.loganalyzer.report.AnalysisReport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReportFormatterTest {
    private final ReportFormatter formatter = new ReportFormatter();

    @Test
    void formatsSummaryErrorsAndTopSectionsDeterministically() {
        AnalysisReport report = sampleReport();

        String summary = formatter.summary(report);
        assertTrue(summary.contains("Valid entries: 2"));
        assertTrue(summary.contains("INFO  1 (50.00%)"));
        assertTrue(summary.contains("ERROR 1 (50.00%)"));

        String errors = formatter.errors(report);
        assertTrue(errors.contains("Errors (ERROR + FATAL): 1"));
        assertTrue(errors.contains("Peak error hour: 2026-11-21T10:00"));

        String top = formatter.top(report);
        assertTrue(top.indexOf("alpha: 2") < top.indexOf("beta: 1"));
        assertTrue(top.indexOf("ServiceException: 3") < top.indexOf("IOException: 1"));
    }

    @Test
    void formatsZeroEntryReportAndFilterTotals() {
        AnalysisReport empty = new AnalysisReport(0, 0, 0, 0, 0,
                Optional.empty(), Optional.empty(), new EnumMap<>(LogLevel.class), new EnumMap<>(LogLevel.class),
                Map.of(), Map.of(), 0, Map.of(), Map.of(), Optional.empty(), Map.of());
        String output = formatter.summary(empty);
        assertTrue(output.contains("Valid entries: 0"));
        assertTrue(output.contains("Earliest timestamp: none"));

        FilterResult result = new FilterResult(5, 0, new DirectoryReadResult(2, 2, 0, 3, java.util.List.of()));
        assertTrue(formatter.filterResult(result).contains("Entries examined: 5\nMatching entries: 0"));
    }

    @Test
    void formatsSingleLineAndIndentedMultilineMessagesWithoutChangingTheirText() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 11, 21, 10, 30, 15);
        String header = "2026-11-21T10:30:15 INFO  Api (app.log:7) - ";
        LogEntry single = new LogEntry(timestamp, LogLevel.INFO, "Api", "ready", Path.of("app.log"), 7);
        assertEquals(header + "ready" + System.lineSeparator(), formatter.entry(single));

        String message = "failure\n    at app.Api.call(Api.java:7)\nCaused by: IOException";
        LogEntry multiline = new LogEntry(timestamp, LogLevel.ERROR, "Api", message, Path.of("app.log"), 7);
        assertEquals("2026-11-21T10:30:15 ERROR Api (app.log:7) - failure\n"
                        + "        at app.Api.call(Api.java:7)\n"
                        + "    Caused by: IOException" + System.lineSeparator(),
                formatter.entry(multiline));
    }

    private static AnalysisReport sampleReport() {
        EnumMap<LogLevel, Long> counts = new EnumMap<>(LogLevel.class);
        EnumMap<LogLevel, Double> percentages = new EnumMap<>(LogLevel.class);
        for (LogLevel level : LogLevel.values()) {
            counts.put(level, 0L);
            percentages.put(level, 0.0);
        }
        counts.put(LogLevel.INFO, 1L);
        counts.put(LogLevel.ERROR, 1L);
        percentages.put(LogLevel.INFO, 50.0);
        percentages.put(LogLevel.ERROR, 50.0);
        Map<String, Long> loggers = new LinkedHashMap<>();
        loggers.put("alpha", 2L);
        loggers.put("beta", 1L);
        Map<String, Long> exceptions = new LinkedHashMap<>();
        exceptions.put("ServiceException", 3L);
        exceptions.put("IOException", 1L);
        LocalDateTime hour = LocalDateTime.of(2026, 11, 21, 10, 0);
        return new AnalysisReport(2, 1, 1, 0, 0, Optional.of(hour), Optional.of(hour), counts,
                percentages, loggers, Map.of(), 1, Map.of("alpha", 1L), Map.of(hour, 1L),
                Optional.of(hour), exceptions);
    }
}
