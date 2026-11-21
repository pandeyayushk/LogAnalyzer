package com.ayush.loganalyzer.filter;

import com.ayush.loganalyzer.model.LogEntry;
import com.ayush.loganalyzer.model.LogLevel;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Immutable optional criteria applied together using AND matching. */
public final class LogFilter {
    private final LogLevel severity;
    private final String keyword;
    private final String loggerName;
    private final LocalDateTime startTimestamp;
    private final LocalDateTime endTimestamp;

    /** Creates a filter; any null criterion is treated as absent. */
    public LogFilter(LogLevel severity, String keyword, String loggerName,
                     LocalDateTime startTimestamp, LocalDateTime endTimestamp) {
        if (keyword != null && keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        if (loggerName != null && loggerName.isBlank()) {
            throw new IllegalArgumentException("loggerName must not be blank");
        }
        if (startTimestamp != null && endTimestamp != null && startTimestamp.isAfter(endTimestamp)) {
            throw new IllegalArgumentException("startTimestamp must not be after endTimestamp");
        }
        this.severity = severity;
        this.keyword = keyword;
        this.loggerName = loggerName;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
    }

    /** Creates a filter with no active criteria. */
    public static LogFilter none() {
        return new LogFilter(null, null, null, null, null);
    }

    public Optional<LogLevel> severity() { return Optional.ofNullable(severity); }
    public Optional<String> keyword() { return Optional.ofNullable(keyword); }
    public Optional<String> loggerName() { return Optional.ofNullable(loggerName); }
    public Optional<LocalDateTime> startTimestamp() { return Optional.ofNullable(startTimestamp); }
    public Optional<LocalDateTime> endTimestamp() { return Optional.ofNullable(endTimestamp); }

    /** Returns whether the entry satisfies every supplied criterion. */
    public boolean matches(LogEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        return (severity == null || entry.level() == severity)
                && (keyword == null || entry.message().toLowerCase(Locale.ROOT)
                        .contains(keyword.toLowerCase(Locale.ROOT)))
                && (loggerName == null || entry.loggerName().equals(loggerName))
                && (startTimestamp == null || !entry.timestamp().isBefore(startTimestamp))
                && (endTimestamp == null || !entry.timestamp().isAfter(endTimestamp));
    }
}
