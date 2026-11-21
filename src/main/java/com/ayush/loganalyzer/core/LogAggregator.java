package com.ayush.loganalyzer.core;

import com.ayush.loganalyzer.model.LogEntry;
import com.ayush.loganalyzer.model.LogLevel;
import com.ayush.loganalyzer.report.AnalysisReport;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Incrementally aggregates entries, retaining counters rather than log records. */
final class LogAggregator {
    /** Finds qualified or simple Java-style type names ending in Exception or Error. */
    private static final Pattern EXCEPTION_CLASS = Pattern.compile(
            "\\b(?:[A-Za-z_$][\\w$]*\\.)*[A-Z_$][\\w$]*(?:Exception|Error)\\b");

    private final EnumMap<LogLevel, Long> severityCounts = new EnumMap<>(LogLevel.class);
    private final Map<String, Long> loggerCounts = new TreeMap<>();
    private final Map<Path, Long> sourceFileCounts = new TreeMap<>(Comparator.comparing(Path::toString));
    private final Map<String, Long> errorCountsByLogger = new TreeMap<>();
    private final Map<LocalDateTime, Long> hourlyErrorCounts = new TreeMap<>();
    private final Map<String, Long> exceptionCounts = new TreeMap<>();
    private long totalEntries;
    private LocalDateTime earliestTimestamp;
    private LocalDateTime latestTimestamp;

    LogAggregator() {
        Arrays.stream(LogLevel.values()).forEach(level -> severityCounts.put(level, 0L));
    }

    void accept(LogEntry entry) {
        totalEntries++;
        severityCounts.merge(entry.level(), 1L, Long::sum);
        loggerCounts.merge(entry.loggerName(), 1L, Long::sum);
        sourceFileCounts.merge(entry.sourceFile(), 1L, Long::sum);
        earliestTimestamp = earliestTimestamp == null || entry.timestamp().isBefore(earliestTimestamp)
                ? entry.timestamp() : earliestTimestamp;
        latestTimestamp = latestTimestamp == null || entry.timestamp().isAfter(latestTimestamp)
                ? entry.timestamp() : latestTimestamp;

        if (entry.level() == LogLevel.ERROR || entry.level() == LogLevel.FATAL) {
            errorCountsByLogger.merge(entry.loggerName(), 1L, Long::sum);
            hourlyErrorCounts.merge(entry.timestamp().truncatedTo(ChronoUnit.HOURS), 1L, Long::sum);
        }
        Matcher matcher = EXCEPTION_CLASS.matcher(entry.message());
        while (matcher.find()) {
            exceptionCounts.merge(matcher.group(), 1L, Long::sum);
        }
    }

    AnalysisReport toReport(DirectoryReadResult readResult) {
        Map<LogLevel, Double> percentages = new EnumMap<>(LogLevel.class);
        for (LogLevel level : LogLevel.values()) {
            percentages.put(level, totalEntries == 0 ? 0.0 : severityCounts.get(level) * 100.0 / totalEntries);
        }
        Map<LocalDateTime, Long> orderedHours = new LinkedHashMap<>(hourlyErrorCounts);
        Optional<LocalDateTime> peakHour = hourlyErrorCounts.entrySet().stream()
                .max(Map.Entry.<LocalDateTime, Long>comparingByValue()
                        .thenComparing(entry -> entry.getKey(), Comparator.reverseOrder()))
                .map(Map.Entry::getKey);

        return new AnalysisReport(totalEntries, readResult.discoveredFiles(), readResult.processedFiles(),
                readResult.skippedFiles(), readResult.malformedLines(), Optional.ofNullable(earliestTimestamp),
                Optional.ofNullable(latestTimestamp), severityCounts, percentages,
                rank(loggerCounts), rank(sourceFileCounts),
                severityCounts.get(LogLevel.ERROR) + severityCounts.get(LogLevel.FATAL),
                rank(errorCountsByLogger), orderedHours, peakHour, exceptionCounts);
    }

    private static <K> Map<K, Long> rank(Map<K, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey((left, right) -> left.toString().compareTo(right.toString()))))
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }
}
