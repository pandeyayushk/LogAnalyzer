package com.ayush.loganalyzer.report;

import com.ayush.loganalyzer.model.LogLevel;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable aggregate statistics for one directory analysis. Ranking maps are ordered best-first. */
public record AnalysisReport(
        long totalEntries,
        long discoveredFiles,
        long processedFiles,
        long skippedFiles,
        long malformedLines,
        Optional<LocalDateTime> earliestTimestamp,
        Optional<LocalDateTime> latestTimestamp,
        Map<LogLevel, Long> severityCounts,
        Map<LogLevel, Double> severityPercentages,
        Map<String, Long> loggerCounts,
        Map<Path, Long> sourceFileCounts,
        long errorEntries,
        Map<String, Long> errorCountsByLogger,
        Map<LocalDateTime, Long> hourlyErrorCounts,
        Optional<LocalDateTime> peakErrorHour,
        Map<String, Long> exceptionCounts) {

    public AnalysisReport {
        earliestTimestamp = MapSupport.requireOptional(earliestTimestamp, "earliestTimestamp");
        latestTimestamp = MapSupport.requireOptional(latestTimestamp, "latestTimestamp");
        peakErrorHour = MapSupport.requireOptional(peakErrorHour, "peakErrorHour");
        severityCounts = MapSupport.immutableMap(severityCounts);
        severityPercentages = MapSupport.immutableMap(severityPercentages);
        loggerCounts = MapSupport.immutableMap(loggerCounts);
        sourceFileCounts = MapSupport.immutableMap(sourceFileCounts);
        errorCountsByLogger = MapSupport.immutableMap(errorCountsByLogger);
        hourlyErrorCounts = MapSupport.immutableMap(hourlyErrorCounts);
        exceptionCounts = MapSupport.immutableMap(exceptionCounts);
    }

    private static final class MapSupport {
        private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }

        private static <T> Optional<T> requireOptional(Optional<T> value, String name) {
            if (value == null) {
                throw new NullPointerException(name + " must not be null");
            }
            return value;
        }
    }
}
