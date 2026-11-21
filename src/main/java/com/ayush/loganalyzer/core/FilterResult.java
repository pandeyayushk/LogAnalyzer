package com.ayush.loganalyzer.core;

import java.util.Objects;

/** Immutable counts and file-processing details for a filtering pass. */
public record FilterResult(long entriesExamined, long matchingEntries, DirectoryReadResult directoryResult) {
    public FilterResult {
        if (entriesExamined < 0 || matchingEntries < 0 || matchingEntries > entriesExamined) {
            throw new IllegalArgumentException("Entry counts must be non-negative and matches cannot exceed examined entries");
        }
        Objects.requireNonNull(directoryResult, "directoryResult must not be null");
    }
}
