package com.ayush.loganalyzer.core;

import com.ayush.loganalyzer.model.LogEntry;
import com.ayush.loganalyzer.parser.LogParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Discovers log files and streams their parsed entries to a consumer. */
public final class LogFileReader {
    private final LogParser parser;

    /** Creates a reader using the standard log header parser. */
    public LogFileReader() {
        this(new LogParser());
    }

    private LogFileReader(LogParser parser) {
        this.parser = parser;
    }

    /**
     * Recursively processes {@code .log} files in deterministic path order.
     *
     * @param directory root directory to process
     * @param entryConsumer receives each complete entry as it is read
     * @return file and malformed-line counts, with paths for any skipped files
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the path does not exist or is not a directory
     * @throws IOException if directory discovery itself fails
     */
    public DirectoryReadResult readDirectory(Path directory, Consumer<LogEntry> entryConsumer)
            throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(entryConsumer, "entryConsumer must not be null");
        if (!Files.exists(directory)) {
            throw new IllegalArgumentException("Directory does not exist: " + directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }

        MutableResult result = new MutableResult();
        processDirectory(directory, entryConsumer, result);
        return result.toResult();
    }

    private void processDirectory(Path directory, Consumer<LogEntry> consumer, MutableResult result)
            throws IOException {
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                children.add(child);
            }
        }
        children.sort(Comparator.comparing(Path::toString));

        for (Path child : children) {
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                processDirectory(child, consumer, result);
            } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)
                    && child.getFileName().toString().endsWith(".log")) {
                result.discoveredFiles++;
                try {
                    processFile(child, consumer, result);
                    result.processedFiles++;
                } catch (IOException exception) {
                    result.skippedFiles++;
                    result.skippedFilePaths.add(child);
                }
            }
        }
    }

    private void processFile(Path file, Consumer<LogEntry> consumer, MutableResult result) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            LogEntry header = null;
            StringBuilder message = null;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                var parsed = parser.parseHeader(line, file, lineNumber);
                if (parsed.isPresent()) {
                    if (header != null) {
                        emit(header, message, consumer);
                    }
                    header = parsed.orElseThrow();
                    message = new StringBuilder(header.message());
                } else if (header == null) {
                    result.malformedLines++;
                } else {
                    message.append('\n').append(line);
                }
            }

            if (header != null) {
                emit(header, message, consumer);
            }
        }
    }

    private static void emit(LogEntry header, StringBuilder message, Consumer<LogEntry> consumer) {
        consumer.accept(new LogEntry(header.timestamp(), header.level(), header.loggerName(), message.toString(),
                header.sourceFile(), header.startLine()));
    }

    private static final class MutableResult {
        private long discoveredFiles;
        private long processedFiles;
        private long skippedFiles;
        private long malformedLines;
        private final List<Path> skippedFilePaths = new ArrayList<>();

        private DirectoryReadResult toResult() {
            return new DirectoryReadResult(discoveredFiles, processedFiles, skippedFiles,
                    malformedLines, skippedFilePaths);
        }
    }
}
