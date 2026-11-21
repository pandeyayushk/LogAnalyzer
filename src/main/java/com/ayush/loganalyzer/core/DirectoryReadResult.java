package com.ayush.loganalyzer.core;

import java.nio.file.Path;
import java.util.List;

/** Summary of a directory processing run. */
public record DirectoryReadResult(
        long discoveredFiles,
        long processedFiles,
        long skippedFiles,
        long malformedLines,
        List<Path> skippedFilePaths) {

    public DirectoryReadResult {
        skippedFilePaths = List.copyOf(skippedFilePaths);
    }
}
