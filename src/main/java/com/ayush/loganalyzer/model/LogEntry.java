package com.ayush.loganalyzer.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

/** One complete log event, including its original location and message content. */
public record LogEntry(
        /** Date and time when the event occurred. */
        LocalDateTime timestamp,
        /** Severity of the event. */
        LogLevel level,
        /** Name of the component or class that emitted the event. */
        String loggerName,
        /** Event text, preserved as read, including any line breaks. */
        String message,
        /** Path to the file containing the event. */
        Path sourceFile,
        /** One based line number where this event begins. */
        long startLine) {

    public LogEntry {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(loggerName, "loggerName must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        if (loggerName.isBlank()) {
            throw new IllegalArgumentException("loggerName must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (startLine <= 0) {
            throw new IllegalArgumentException("startLine must be positive");
        }
    }
}
