package com.ayush.loganalyzer.parser;

import com.ayush.loganalyzer.model.LogEntry;
import com.ayush.loganalyzer.model.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LogParserTest {
    private static final Path SOURCE = Path.of("logs", "application.log");
    private final LogParser parser = new LogParser();

    @ParameterizedTest
    @EnumSource(LogLevel.class)
    void parsesEverySupportedSeverity(LogLevel level) {
        Optional<LogEntry> parsed = parser.parseHeader(
                "2026-11-21 10:30:15 " + level + " AuthService - User login successful", SOURCE, 12);

        assertTrue(parsed.isPresent());
        assertEquals(level, parsed.orElseThrow().level());
    }

    @Test
    void parsesFieldsAndPreservesLocation() {
        LogEntry entry = parser.parseHeader("2026-11-21 10:30:15 INFO AuthService - User login successful",
                SOURCE, 42).orElseThrow();

        assertEquals(LocalDateTime.of(2026, 11, 21, 10, 30, 15), entry.timestamp());
        assertEquals("AuthService", entry.loggerName());
        assertEquals("User login successful", entry.message());
        assertEquals(SOURCE, entry.sourceFile());
        assertEquals(42, entry.startLine());
    }

    @ParameterizedTest
    @ValueSource(strings = {"trace", "Debug", "iNfO", "warn", "Error", "fatal"})
    void parsesSeverityWithoutCaseSensitivity(String severity) {
        assertTrue(parser.parseHeader("2026-11-21 10:30:15 " + severity + " Logger - message", SOURCE, 1).isPresent());
    }

    @Test
    void rejectsInvalidTimestampFormatsAndImpossibleDates() {
        assertMalformed("2026/11/21 10:30:15 INFO Logger - message");
        assertMalformed("2026-1-21 10:30:15 INFO Logger - message");
        assertMalformed("2026-02-30 10:30:15 INFO Logger - message");
        assertMalformed("2026-11-21 25:30:15 INFO Logger - message");
    }

    @Test
    void rejectsUnknownSeverityAndMissingFields() {
        assertMalformed("2026-11-21 10:30:15 NOTICE Logger - message");
        assertMalformed("2026-11-21 10:30:15 INFO  - message");
        assertMalformed("2026-11-21 10:30:15 INFO Logger - ");
        assertMalformed("2026-11-21 10:30:15 INFO Logger -   ");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "not a header", "2026-11-21 10:30:15 INFO Logger: message",
            "2026-11-21 10:30:15 INFO Logger -- message",
            "2026-11-21T10:30:15 INFO Logger - message"
    })
    void rejectsIncorrectDelimitersAndMalformedHeaders(String line) {
        assertMalformed(line);
    }

    @Test
    void preservesLeadingAndTrailingMessageWhitespace() {
        String message = "  keep leading and trailing spaces  ";
        LogEntry entry = parser.parseHeader("2026-11-21 10:30:15 INFO Logger - " + message, SOURCE, 1)
                .orElseThrow();
        assertEquals(message, entry.message());
    }

    @Test
    void rejectsNullAndInvalidMethodArguments() {
        assertThrows(NullPointerException.class, () -> parser.parseHeader(null, SOURCE, 1));
        assertThrows(NullPointerException.class, () -> parser.parseHeader("line", null, 1));
        assertThrows(IllegalArgumentException.class, () -> parser.parseHeader("line", SOURCE, 0));
        assertThrows(IllegalArgumentException.class, () -> parser.parseHeader("line", SOURCE, -1));
    }

    private void assertMalformed(String line) {
        assertEquals(Optional.empty(), parser.parseHeader(line, SOURCE, 1), line);
    }
}
