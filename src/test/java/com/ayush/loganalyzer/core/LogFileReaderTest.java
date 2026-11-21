package com.ayush.loganalyzer.core;

import com.ayush.loganalyzer.model.LogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LogFileReaderTest {
    @TempDir
    Path tempDirectory;

    private final LogFileReader reader = new LogFileReader();

    @Test
    void processesOneLogFileAndStreamsItsSingleEntry() throws IOException {
        Path file = write(tempDirectory.resolve("one.log"), header("first"));
        List<LogEntry> entries = new ArrayList<>();

        DirectoryReadResult result = reader.readDirectory(tempDirectory, entries::add);

        assertCounts(result, 1, 1, 0, 0);
        assertEquals(1, entries.size());
        assertEquals("first", entries.getFirst().message());
        assertEquals(file, entries.getFirst().sourceFile());
    }

    @Test
    void discoversNestedLogFilesAndIgnoresOtherExtensions() throws IOException {
        write(tempDirectory.resolve("top.log"), header("top"));
        Path nested = Files.createDirectories(tempDirectory.resolve("nested"));
        write(nested.resolve("inner.log"), header("inner"));
        write(tempDirectory.resolve("notes.txt"), header("ignored"));
        List<LogEntry> entries = new ArrayList<>();

        DirectoryReadResult result = reader.readDirectory(tempDirectory, entries::add);

        assertCounts(result, 2, 2, 0, 0);
        assertEquals(List.of("inner", "top"), entries.stream().map(LogEntry::message).toList());
    }

    @Test
    void processesFilesInDeterministicSortedPathOrder() throws IOException {
        write(tempDirectory.resolve("z.log"), header("z"));
        write(tempDirectory.resolve("a.log"), header("a"));
        Path nested = Files.createDirectories(tempDirectory.resolve("m"));
        write(nested.resolve("b.log"), header("b"));
        List<LogEntry> entries = new ArrayList<>();

        reader.readDirectory(tempDirectory, entries::add);

        assertEquals(List.of("a", "b", "z"), entries.stream().map(LogEntry::message).toList());
    }

    @Test
    void handlesDirectoriesWithoutLogsAndEmptyLogFiles() throws IOException {
        DirectoryReadResult emptyResult = reader.readDirectory(tempDirectory, entry -> fail("no entries expected"));
        assertCounts(emptyResult, 0, 0, 0, 0);

        write(tempDirectory.resolve("empty.log"), "");
        DirectoryReadResult result = reader.readDirectory(tempDirectory, entry -> fail("no entries expected"));
        assertCounts(result, 1, 1, 0, 0);
    }

    @Test
    void emitsSingleLineAndMultipleEntriesWithStartingLines() throws IOException {
        Path file = write(tempDirectory.resolve("events.log"), String.join("\n",
                "orphan one", header("first"), "orphan-like continuation", header("second")));
        List<LogEntry> entries = new ArrayList<>();

        DirectoryReadResult result = reader.readDirectory(tempDirectory, entries::add);

        assertCounts(result, 1, 1, 0, 1);
        assertEquals(2, entries.size());
        assertEquals(2, entries.get(0).startLine());
        assertEquals(4, entries.get(1).startLine());
        assertEquals(file, entries.get(0).sourceFile());
        assertEquals("first\norphan-like continuation", entries.get(0).message());
    }

    @Test
    void attachesStackTraceAndContinuationLinesToPreviousEntry() throws IOException {
        String content = String.join("\n", header("failure"), "java.lang.IllegalStateException: bad",
                "\tat example.Service.run(Service.java:8)", "", header("recovered"));
        write(tempDirectory.resolve("stack.log"), content);
        List<LogEntry> entries = new ArrayList<>();

        DirectoryReadResult result = reader.readDirectory(tempDirectory, entries::add);

        assertCounts(result, 1, 1, 0, 0);
        assertEquals("failure\njava.lang.IllegalStateException: bad\n\tat example.Service.run(Service.java:8)\n",
                entries.getFirst().message());
        assertEquals("recovered", entries.get(1).message());
    }

    @Test
    void emitsFinalEntryAtEndOfFileAndCountsOnlyOrphanLines() throws IOException {
        write(tempDirectory.resolve("last.log"), String.join("\n", "bad one", "bad two", header("final"), "tail"));
        List<LogEntry> entries = new ArrayList<>();

        DirectoryReadResult result = reader.readDirectory(tempDirectory, entries::add);

        assertCounts(result, 1, 1, 0, 2);
        assertEquals(1, entries.size());
        assertEquals("final\ntail", entries.getFirst().message());
    }

    @Test
    void consumerReceivesEntriesImmediatelyAndItsExceptionsPropagate() throws IOException {
        write(tempDirectory.resolve("stream.log"), String.join("\n", header("one"), header("two")));
        AtomicInteger received = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("consumer failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> reader.readDirectory(tempDirectory, entry -> {
                    assertEquals(0, received.getAndIncrement());
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(1, received.get());
    }

    @Test
    void validatesDirectoryAndConsumerArguments() throws IOException {
        assertThrows(NullPointerException.class, () -> reader.readDirectory(null, entry -> {}));
        assertThrows(NullPointerException.class, () -> reader.readDirectory(tempDirectory, null));
        assertThrows(IllegalArgumentException.class,
                () -> reader.readDirectory(tempDirectory.resolve("missing"), entry -> {}));
        Path file = write(tempDirectory.resolve("file.txt"), "text");
        assertThrows(IllegalArgumentException.class, () -> reader.readDirectory(file, entry -> {}));
    }

    @Test
    void skipsUnreadableLogFileAndContinuesWhenPlatformPermissionsAllow() throws IOException {
        assumeTrue(Files.getFileStore(tempDirectory).supportsFileAttributeView("posix"));
        Path unreadable = write(tempDirectory.resolve("a-unreadable.log"), header("hidden"));
        Path readable = write(tempDirectory.resolve("b-readable.log"), header("visible"));
        Files.setPosixFilePermissions(unreadable, EnumSet.of(PosixFilePermission.OWNER_WRITE));
        assumeTrue(!Files.isReadable(unreadable), "Current user can bypass the file's read permissions");
        List<LogEntry> entries = new ArrayList<>();

        DirectoryReadResult result = reader.readDirectory(tempDirectory, entries::add);

        assertCounts(result, 2, 1, 1, 0);
        assertEquals(List.of(unreadable), result.skippedFilePaths());
        assertEquals(List.of("visible"), entries.stream().map(LogEntry::message).toList());
    }

    private static Path write(Path path, String content) throws IOException {
        Files.writeString(path, content, java.nio.charset.StandardCharsets.UTF_8);
        return path;
    }

    private static String header(String message) {
        return "2026-11-21 10:30:15 INFO Logger - " + message;
    }

    private static void assertCounts(DirectoryReadResult result, long discovered, long processed,
                                     long skipped, long malformed) {
        assertEquals(discovered, result.discoveredFiles());
        assertEquals(processed, result.processedFiles());
        assertEquals(skipped, result.skippedFiles());
        assertEquals(malformed, result.malformedLines());
    }
}
