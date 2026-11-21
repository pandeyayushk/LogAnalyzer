package com.ayush.loganalyzer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogLevelTest {
    @Test
    void definesEverySupportedLevel() {
        assertEquals(java.util.List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"),
                java.util.Arrays.stream(LogLevel.values()).map(Enum::name).toList());
    }

    @Test
    void parsesNamesCaseInsensitively() {
        for (LogLevel level : LogLevel.values()) {
            assertEquals(level, LogLevel.fromString(level.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    @Test
    void rejectsNullBlankAndUnknownNames() {
        assertThrows(IllegalArgumentException.class, () -> LogLevel.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> LogLevel.fromString(" \t"));
        assertThrows(IllegalArgumentException.class, () -> LogLevel.fromString("NOTICE"));
    }
}
