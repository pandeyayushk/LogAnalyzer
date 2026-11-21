package com.ayush.loganalyzer.parser;

import com.ayush.loganalyzer.model.LogEntry;
import com.ayush.loganalyzer.model.LogLevel;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses individual log-header lines in the supported timestamp/level/logger format. */
public final class LogParser {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) "
                    + "(\\S+) (\\S+) - (.*)$");

    /** Creates a parser for the supported log-header format. */
    public LogParser() {
    }

    /**
     * Parses one header line into a log entry.
     *
     * @param line input line
     * @param sourceFile path of the file containing the line
     * @param lineNumber one-based line number
     * @return the parsed entry, or empty when the line is malformed
     * @throws NullPointerException if {@code line} or {@code sourceFile} is null
     * @throws IllegalArgumentException if {@code lineNumber} is not positive
     */
    public Optional<LogEntry> parseHeader(String line, Path sourceFile, long lineNumber) {
        Objects.requireNonNull(line, "line must not be null");
        Objects.requireNonNull(sourceFile, "sourceFile must not be null");
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }

        Matcher matcher = HEADER_PATTERN.matcher(line);
        if (!matcher.matches() || matcher.group(4).isBlank()) {
            return Optional.empty();
        }

        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(matcher.group(1), TIMESTAMP_FORMAT);
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }

        LogLevel level;
        try {
            level = LogLevel.fromString(matcher.group(2));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        return Optional.of(new LogEntry(timestamp, level, matcher.group(3), matcher.group(4),
                sourceFile, lineNumber));
    }
}
