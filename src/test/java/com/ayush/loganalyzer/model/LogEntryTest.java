package com.ayush.loganalyzer.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LogEntryTest {
    private static final LocalDateTime TIMESTAMP = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
    private static final Path FILE = Path.of("application.log");

    @Test
    void constructsRecordWithProvidedValues() {
        LogEntry entry = new LogEntry(TIMESTAMP, LogLevel.INFO, "com.example.App", "started", FILE, 1);
        assertEquals(TIMESTAMP, entry.timestamp());
        assertEquals(LogLevel.INFO, entry.level());
        assertEquals("com.example.App", entry.loggerName());
        assertEquals("started", entry.message());
        assertEquals(FILE, entry.sourceFile());
        assertEquals(1, entry.startLine());
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> new LogEntry(null, LogLevel.INFO, "logger", "message", FILE, 1));
        assertThrows(NullPointerException.class, () -> new LogEntry(TIMESTAMP, null, "logger", "message", FILE, 1));
        assertThrows(NullPointerException.class, () -> new LogEntry(TIMESTAMP, LogLevel.INFO, null, "message", FILE, 1));
        assertThrows(NullPointerException.class, () -> new LogEntry(TIMESTAMP, LogLevel.INFO, "logger", null, FILE, 1));
        assertThrows(NullPointerException.class, () -> new LogEntry(TIMESTAMP, LogLevel.INFO, "logger", "message", null, 1));
    }

    @Test
    void rejectsBlankLoggerAndMessage() {
        assertThrows(IllegalArgumentException.class, () -> new LogEntry(TIMESTAMP, LogLevel.INFO, " \t", "message", FILE, 1));
        assertThrows(IllegalArgumentException.class, () -> new LogEntry(TIMESTAMP, LogLevel.INFO, "logger", "\n ", FILE, 1));
    }

    @Test
    void rejectsNonPositiveStartLine() {
        assertThrows(IllegalArgumentException.class, () -> new LogEntry(TIMESTAMP, LogLevel.INFO, "logger", "message", FILE, 0));
        assertThrows(IllegalArgumentException.class, () -> new LogEntry(TIMESTAMP, LogLevel.INFO, "logger", "message", FILE, -1));
    }

    @Test
    void preservesMultilineMessageExactly() {
        String message = " first line\r\nsecond line\n  third line  ";
        LogEntry entry = new LogEntry(TIMESTAMP, LogLevel.ERROR, "logger", message, FILE, 8);
        assertSame(message, entry.message());
    }

    @Test
    void isImmutableRecord() {
        assertTrue(java.lang.reflect.Modifier.isFinal(LogEntry.class.getModifiers()));
        assertTrue(LogEntry.class.isRecord());
        assertTrue(java.lang.reflect.Modifier.isFinal(LogLevel.class.getModifiers()));
    }
}
