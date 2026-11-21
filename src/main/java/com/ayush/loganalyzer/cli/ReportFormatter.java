package com.ayush.loganalyzer.cli;

import com.ayush.loganalyzer.core.FilterResult;
import com.ayush.loganalyzer.model.LogEntry;
import com.ayush.loganalyzer.model.LogLevel;
import com.ayush.loganalyzer.report.AnalysisReport;

import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/** Formats reports and streamed entries for terminal output. */
public final class ReportFormatter {
    public String summary(AnalysisReport report) {
        StringBuilder output = new StringBuilder("Analysis summary\n");
        output.append("Valid entries: ").append(report.totalEntries()).append('\n')
                .append("Files: ").append(report.processedFiles()).append(" processed / ")
                .append(report.discoveredFiles()).append(" discovered / ").append(report.skippedFiles())
                .append(" skipped\n")
                .append("Malformed/orphan lines: ").append(report.malformedLines()).append('\n')
                .append("Earliest timestamp: ").append(report.earliestTimestamp().map(Object::toString).orElse("none"))
                .append('\n')
                .append("Latest timestamp: ").append(report.latestTimestamp().map(Object::toString).orElse("none"))
                .append('\n')
                .append("Severity counts:\n");
        for (LogLevel level : LogLevel.values()) {
            output.append(String.format(Locale.ROOT, "  %-5s %d (%.2f%%)%n", level,
                    report.severityCounts().getOrDefault(level, 0L),
                    report.severityPercentages().getOrDefault(level, 0.0)));
        }
        return output.toString();
    }

    public String errors(AnalysisReport report) {
        StringBuilder output = new StringBuilder("Errors (ERROR + FATAL): ")
                .append(report.errorEntries()).append('\n');
        appendMap(output, "Error counts by logger", report.errorCountsByLogger());
        appendMap(output, "Hourly error counts", report.hourlyErrorCounts());
        output.append("Peak error hour: ").append(report.peakErrorHour().map(Object::toString).orElse("none"))
                .append('\n');
        return output.toString();
    }

    public String top(AnalysisReport report) {
        StringBuilder output = new StringBuilder();
        appendMap(output, "Top loggers", report.loggerCounts());
        appendMap(output, "Top source files", report.sourceFileCounts());
        appendMap(output, "Top exception classes", report.exceptionCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new)));
        return output.toString();
    }

    public String entry(LogEntry entry) {
        return String.format(Locale.ROOT, "%s %-5s %s (%s:%d) - %s%n", entry.timestamp(), entry.level(),
                entry.loggerName(), entry.sourceFile(), entry.startLine(), entry.message());
    }

    public String filterResult(FilterResult result) {
        return "Entries examined: " + result.entriesExamined() + "\nMatching entries: "
                + result.matchingEntries() + "\nFiles: " + result.directoryResult().processedFiles()
                + " processed / " + result.directoryResult().discoveredFiles() + " discovered / "
                + result.directoryResult().skippedFiles() + " skipped\nMalformed/orphan lines: "
                + result.directoryResult().malformedLines() + "\n";
    }

    private static void appendMap(StringBuilder output, String heading, Map<?, Long> values) {
        output.append(heading).append(':').append('\n');
        if (values.isEmpty()) {
            output.append("  none\n");
            return;
        }
        values.forEach((key, value) -> output.append("  ").append(key).append(": ").append(value).append('\n'));
    }
}
