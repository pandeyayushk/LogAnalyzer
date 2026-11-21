package com.ayush.loganalyzer.core;

import com.ayush.loganalyzer.filter.LogFilter;
import com.ayush.loganalyzer.model.LogEntry;
import com.ayush.loganalyzer.model.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LogFilterServiceTest {
    @TempDir
    Path directory;

    private final LogFilterService service = new LogFilterService();

    @Test
    void filtersBySeverityAndKeywordCaseInsensitively() throws IOException {
        fixture();

        List<LogEntry> severityMatches = new ArrayList<>();
        FilterResult severityResult = service.filter(directory,
                new LogFilter(LogLevel.ERROR, null, null, null, null), severityMatches::add);
        assertEquals(4, severityResult.entriesExamined());
        assertEquals(1, severityResult.matchingEntries());
        assertEquals(LogLevel.ERROR, severityMatches.getFirst().level());

        List<LogEntry> keywordMatches = new ArrayList<>();
        FilterResult keywordResult = service.filter(directory,
                new LogFilter(null, "bAd CREDENTIALS", null, null, null), keywordMatches::add);
        assertEquals(1, keywordResult.matchingEntries());
        assertEquals("Bad credentials", keywordMatches.getFirst().message());
    }

    @Test
    void matchesExactLoggerAndInclusiveTimeBounds() throws IOException {
        fixture();
        LocalDateTime boundary = LocalDateTime.of(2026, 11, 21, 10, 30, 15);

        List<LogEntry> loggerMatches = new ArrayList<>();
        service.filter(directory, new LogFilter(null, null, "AuthService", null, null), loggerMatches::add);
        assertEquals(List.of("User login successful", "Bad credentials", "Session checked"),
                loggerMatches.stream().map(LogEntry::message).toList());

        List<LogEntry> rangeMatches = new ArrayList<>();
        FilterResult result = service.filter(directory, new LogFilter(null, null, null, boundary, boundary),
                rangeMatches::add);
        assertEquals(2, result.matchingEntries());
        assertTrue(rangeMatches.stream().allMatch(entry -> entry.timestamp().equals(boundary)));
    }

    @Test
    void startAndEndTimestampBoundsAreIndividuallyInclusive() throws IOException {
        fixture();
        LocalDateTime boundary = LocalDateTime.of(2026, 11, 21, 10, 30, 15);
        List<LogEntry> afterStart = new ArrayList<>();
        FilterResult startResult = service.filter(directory,
                new LogFilter(null, null, null, boundary, null), afterStart::add);
        assertEquals(3, startResult.matchingEntries());
        assertTrue(afterStart.stream().allMatch(entry -> !entry.timestamp().isBefore(boundary)));
        assertTrue(afterStart.stream().anyMatch(entry -> entry.timestamp().equals(boundary)));

        List<LogEntry> beforeEnd = new ArrayList<>();
        FilterResult endResult = service.filter(directory,
                new LogFilter(null, null, null, null, boundary), beforeEnd::add);
        assertEquals(3, endResult.matchingEntries());
        assertTrue(beforeEnd.stream().allMatch(entry -> !entry.timestamp().isAfter(boundary)));
        assertTrue(beforeEnd.stream().anyMatch(entry -> entry.timestamp().equals(boundary)));
    }

    @Test
    void combinesCriteriaUsingAndLogicAndDoesNotSearchLoggerOrPathForKeyword() throws IOException {
        fixture();
        LogFilter combined = new LogFilter(LogLevel.ERROR, "CREDENTIALS", "AuthService",
                LocalDateTime.of(2026, 11, 21, 10, 30, 15),
                LocalDateTime.of(2026, 11, 21, 10, 30, 15));
        List<LogEntry> matches = new ArrayList<>();

        FilterResult result = service.filter(directory, combined, matches::add);

        assertEquals(4, result.entriesExamined());
        assertEquals(1, result.matchingEntries());
        assertEquals("Bad credentials", matches.getFirst().message());

        FilterResult loggerOnlyKeyword = service.filter(directory,
                new LogFilter(null, "AuthService", null, null, null), entry -> fail("keyword searches messages only"));
        assertEquals(0, loggerOnlyKeyword.matchingEntries());
    }

    @Test
    void emptyFilterMatchesAllEntriesAndNoMatchReturnsZero() throws IOException {
        fixture();
        List<LogEntry> all = new ArrayList<>();
        FilterResult allResult = service.filter(directory, LogFilter.none(), all::add);
        assertEquals(4, allResult.entriesExamined());
        assertEquals(4, allResult.matchingEntries());
        assertEquals(4, all.size());

        FilterResult none = service.filter(directory,
                new LogFilter(LogLevel.FATAL, null, null, null, null), entry -> fail("no match expected"));
        assertEquals(4, none.entriesExamined());
        assertEquals(0, none.matchingEntries());
    }

    @Test
    void handlesEmptyDirectoryAndReturnsReaderStatistics() throws IOException {
        FilterResult empty = service.filter(directory, LogFilter.none(), entry -> fail("no entries expected"));
        assertEquals(0, empty.entriesExamined());
        assertEquals(0, empty.matchingEntries());
        assertEquals(0, empty.directoryResult().discoveredFiles());

        write("events.log", "orphan line\n" + header("2026-11-21 10:30:15", "INFO", "App", "valid"));
        FilterResult withMalformed = service.filter(directory, LogFilter.none(), entry -> {});
        assertEquals(1, withMalformed.entriesExamined());
        assertEquals(1, withMalformed.directoryResult().malformedLines());
        assertEquals(1, withMalformed.directoryResult().processedFiles());
    }

    @Test
    void validatesNullAndInvalidFilterCriteria() throws IOException {
        assertThrows(NullPointerException.class, () -> service.filter(directory, null, entry -> {}));
        assertThrows(NullPointerException.class, () -> service.filter(directory, LogFilter.none(), null));
        assertThrows(NullPointerException.class, () -> service.filter(null, LogFilter.none(), entry -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> new LogFilter(null, " \t", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LogFilter(null, null, " ", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LogFilter(null, null, null, LocalDateTime.MAX, LocalDateTime.MIN));
    }

    @Test
    void consumerReceivesMatchesIncrementallyAndExceptionsPropagate() throws IOException {
        write("stream.log", String.join("\n",
                header("2026-11-21 10:30:15", "INFO", "A", "first"),
                header("2026-11-21 10:30:16", "INFO", "A", "second")));
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("consumer error");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.filter(directory, LogFilter.none(), entry -> {
                    calls.incrementAndGet();
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(1, calls.get());
    }

    @Test
    void repeatedFilteringReReadsChangedSourceFiles() throws IOException {
        Path log = write("reloaded.log", header("2026-11-21 10:30:15", "INFO", "App", "initial"));
        List<LogEntry> first = new ArrayList<>();
        FilterResult firstResult = service.filter(directory, LogFilter.none(), first::add);

        Files.writeString(log, header("2026-11-21 10:30:16", "ERROR", "App", "updated"));
        List<LogEntry> second = new ArrayList<>();
        FilterResult secondResult = service.filter(directory,
                new LogFilter(LogLevel.ERROR, null, null, null, null), second::add);

        assertEquals(1, firstResult.matchingEntries());
        assertEquals(1, secondResult.matchingEntries());
        assertEquals("initial", first.getFirst().message());
        assertEquals("updated", second.getFirst().message());
    }

    @Test
    void reportsSkippedFileStatisticsWhenPermissionsCanBeEnforced() throws IOException {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path unreadable = write("a-unreadable.log", header("2026-11-21 10:30:15", "INFO", "A", "hidden"));
        write("b-readable.log", header("2026-11-21 10:30:15", "INFO", "B", "visible"));
        Files.setPosixFilePermissions(unreadable, EnumSet.of(PosixFilePermission.OWNER_WRITE));
        assumeTrue(!Files.isReadable(unreadable), "Current user can bypass file read permissions");

        List<LogEntry> results = new ArrayList<>();
        FilterResult result = service.filter(directory, LogFilter.none(), results::add);

        assertEquals(1, result.entriesExamined());
        assertEquals(1, result.matchingEntries());
        assertEquals(2, result.directoryResult().discoveredFiles());
        assertEquals(1, result.directoryResult().skippedFiles());
        assertEquals(List.of(unreadable), result.directoryResult().skippedFilePaths());
    }

    private void fixture() throws IOException {
        write("events.log", String.join("\n",
                header("2026-11-21 10:30:14", "INFO", "AuthService", "User login successful"),
                header("2026-11-21 10:30:15", "ERROR", "AuthService", "Bad credentials"),
                header("2026-11-21 10:30:15", "WARN", "Storage", "Disk almost full"),
                header("2026-11-21 10:30:16", "DEBUG", "AuthService", "Session checked")));
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String header(String timestamp, String level, String logger, String message) {
        return timestamp + " " + level + " " + logger + " - " + message;
    }
}
