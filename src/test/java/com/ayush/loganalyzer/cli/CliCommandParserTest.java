package com.ayush.loganalyzer.cli;

import com.ayush.loganalyzer.model.LogLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CliCommandParserTest {
    private final CliCommandParser parser = new CliCommandParser();

    @Test
    void tokenizesQuotedArgumentsAndPreservesWindowsPaths() {
        assertEquals(List.of("filter", "C:\\logs with spaces", "--keyword", "disk almost full"),
                parser.tokenize("filter \"C:\\logs with spaces\" --keyword 'disk almost full'"));
    }

    @Test
    void rejectsUnclosedQuotes() {
        assertThrows(IllegalArgumentException.class, () -> parser.tokenize("analyze \"unfinished"));
    }

    @Test
    void parsesAllFilterCriteria() {
        var parsed = parser.parseFilter(List.of("folder", "--severity", "error", "--keyword", "timeout",
                "--logger", "Storage", "--from", "2026-11-21T10:00:00", "--to", "2026-11-21T11:00:00"));

        assertEquals("folder", parsed.directory().toString());
        assertEquals(LogLevel.ERROR, parsed.filter().severity().orElseThrow());
        assertEquals("timeout", parsed.filter().keyword().orElseThrow());
        assertEquals("Storage", parsed.filter().loggerName().orElseThrow());
        assertEquals(LocalDateTime.of(2026, 11, 21, 10, 0), parsed.filter().startTimestamp().orElseThrow());
        assertEquals(LocalDateTime.of(2026, 11, 21, 11, 0), parsed.filter().endTimestamp().orElseThrow());
    }

    @Test
    void rejectsMissingUnknownDuplicateAndMalformedFilterOptions() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseFilter(List.of()));
        assertThrows(IllegalArgumentException.class, () -> parser.parseFilter(List.of("--severity", "INFO")));
        assertThrows(IllegalArgumentException.class, () -> parser.parseFilter(List.of("folder", "--keyword")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFilter(List.of("folder", "--wat", "x")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFilter(List.of("folder", "--keyword", "x", "--keyword", "y")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFilter(List.of("folder", "--severity", "notice")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFilter(List.of("folder", "--from", "yesterday")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFilter(List.of("folder", "--keyword", " ")));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseFilter(List.of("folder", "--from", "2026-12-01T00:00:00",
                        "--to", "2026-01-01T00:00:00")));
    }
}
