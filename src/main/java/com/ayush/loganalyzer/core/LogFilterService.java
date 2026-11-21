package com.ayush.loganalyzer.core;

import com.ayush.loganalyzer.filter.LogFilter;
import com.ayush.loganalyzer.model.LogEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/** Re-reads a directory and streams entries matching a filter to a consumer. */
public final class LogFilterService {
    private final LogFileReader fileReader;

    /** Creates a filter service using the standard streaming file reader. */
    public LogFilterService() {
        this.fileReader = new LogFileReader();
    }

    /** Applies a filter to entries streamed from the directory. */
    public FilterResult filter(Path directory, LogFilter filter, Consumer<LogEntry> consumer) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(filter, "filter must not be null");
        Objects.requireNonNull(consumer, "consumer must not be null");

        long[] counts = new long[2];
        DirectoryReadResult readResult = fileReader.readDirectory(directory, entry -> {
            counts[0]++;
            if (filter.matches(entry)) {
                counts[1]++;
                consumer.accept(entry);
            }
        });
        return new FilterResult(counts[0], counts[1], readResult);
    }
}
