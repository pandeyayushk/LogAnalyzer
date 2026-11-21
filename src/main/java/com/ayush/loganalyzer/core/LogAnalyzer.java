package com.ayush.loganalyzer.core;

import com.ayush.loganalyzer.report.AnalysisReport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Coordinates directory reading and incremental log aggregation. */
public final class LogAnalyzer {
    private final LogFileReader fileReader;

    /** Creates an analyzer with a standard log file reader. */
    public LogAnalyzer() {
        this(new LogFileReader());
    }

    private LogAnalyzer(LogFileReader fileReader) {
        this.fileReader = fileReader;
    }

    /** Analyzes all log entries in a directory without retaining the entries. */
    public AnalysisReport analyze(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory must not be null");
        LogAggregator aggregator = new LogAggregator();
        DirectoryReadResult readResult = fileReader.readDirectory(directory, aggregator::accept);
        return aggregator.toReport(readResult);
    }
}
