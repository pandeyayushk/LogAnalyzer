package com.ayush.loganalyzer.cli;

import com.ayush.loganalyzer.model.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LogAnalyzerCliTest {
    @TempDir
    Path directory;

    @Test
    void supportsHelpAndExitCommands() {
        LogAnalyzerCli cli = new LogAnalyzerCli();
        StringWriter buffer = new StringWriter();
        PrintWriter output = new PrintWriter(buffer);

        assertTrue(cli.execute("help", output));
        assertTrue(buffer.toString().contains("analyze <folder>"));
        assertFalse(cli.execute("exit", output));
        assertTrue(buffer.toString().contains("Goodbye."));
    }

    @Test
    void analyzeSetsLatestReportAndSummaryErrorsTopReuseIt() throws Exception {
        Files.writeString(directory.resolve("app.log"), String.join("\n",
                "2026-11-21 10:30:15 INFO Api - ready",
                "2026-11-21 10:31:15 ERROR Api - java.io.IOException: unavailable"));
        LogAnalyzerCli cli = new LogAnalyzerCli();
        StringWriter buffer = new StringWriter();
        PrintWriter output = new PrintWriter(buffer);

        assertTrue(cli.execute("analyze \"" + directory + "\"", output));
        assertTrue(cli.latestReport().isPresent());
        assertTrue(buffer.toString().contains("Valid entries: 2"));
        buffer.getBuffer().setLength(0);

        cli.execute("summary", output);
        assertTrue(buffer.toString().contains("Analysis summary"));
        buffer.getBuffer().setLength(0);
        cli.execute("errors", output);
        assertTrue(buffer.toString().contains("Errors (ERROR + FATAL): 1"));
        buffer.getBuffer().setLength(0);
        cli.execute("top", output);
        assertTrue(buffer.toString().contains("java.io.IOException: 1"));
    }

    @Test
    void invalidAnalysisDoesNotReplaceLatestSuccessfulReport() throws Exception {
        Files.writeString(directory.resolve("one.log"), "2026-11-21 10:30:15 INFO App - one");
        LogAnalyzerCli cli = new LogAnalyzerCli();
        StringWriter buffer = new StringWriter();
        PrintWriter output = new PrintWriter(buffer);
        cli.execute("analyze " + directory, output);
        var previous = cli.latestReport().orElseThrow();
        buffer.getBuffer().setLength(0);

        cli.execute("analyze " + directory.resolve("missing"), output);

        assertSame(previous, cli.latestReport().orElseThrow());
        assertTrue(buffer.toString().contains("Error:"));
    }

    @Test
    void filterCommandStreamsMatchingEntriesAndValidatesArguments() throws Exception {
        Files.writeString(directory.resolve("filter.log"), String.join("\n",
                "2026-11-21 10:30:15 INFO App - ordinary",
                "2026-11-21 10:31:15 ERROR App - connection failed"));
        LogAnalyzerCli cli = new LogAnalyzerCli();
        StringWriter buffer = new StringWriter();
        PrintWriter output = new PrintWriter(buffer);

        cli.execute("filter " + directory + " --severity error --keyword CONNECTION", output);
        assertTrue(buffer.toString().contains("ERROR App"));
        assertTrue(buffer.toString().contains("Matching entries: 1"));
        buffer.getBuffer().setLength(0);

        cli.execute("filter " + directory + " --severity unknown", output);
        assertTrue(buffer.toString().contains("Invalid severity"));
    }

    @Test
    void reportsEmptyDirectoryZeroResultsMissingArgumentsAndInvalidDirectory() {
        LogAnalyzerCli cli = new LogAnalyzerCli();
        StringWriter buffer = new StringWriter();
        PrintWriter output = new PrintWriter(buffer);

        cli.execute("analyze " + directory, output);
        assertTrue(buffer.toString().contains("Valid entries: 0"));
        assertTrue(cli.latestReport().orElseThrow().earliestTimestamp().isEmpty());
        buffer.getBuffer().setLength(0);

        cli.execute("analyze", output);
        assertTrue(buffer.toString().contains("Usage: analyze <folder>"));
        buffer.getBuffer().setLength(0);

        cli.execute("filter " + directory.resolve("missing"), output);
        assertTrue(buffer.toString().contains("does not exist"));
        buffer.getBuffer().setLength(0);

        cli.execute("summary extra", output);
        assertTrue(buffer.toString().contains("does not accept arguments"));
    }

    @Test
    void rejectsExtraArgumentsAndUnknownCommandsWithoutExiting() {
        LogAnalyzerCli cli = new LogAnalyzerCli();
        StringWriter buffer = new StringWriter();
        PrintWriter output = new PrintWriter(buffer);

        assertTrue(cli.execute("help extra", output));
        assertTrue(buffer.toString().contains("Usage: help"));
        buffer.getBuffer().setLength(0);
        assertTrue(cli.execute("nonsense", output));
        assertTrue(buffer.toString().contains("Unknown command"));
        buffer.getBuffer().setLength(0);
        assertTrue(cli.execute("summary extra", output));
        assertTrue(buffer.toString().contains("does not accept arguments"));
    }

    @Test
    void catchesMalformedCommandAndTimestampErrors() {
        LogAnalyzerCli cli = new LogAnalyzerCli();
        StringWriter buffer = new StringWriter();
        PrintWriter output = new PrintWriter(buffer);

        cli.execute("analyze \"open", output);
        assertTrue(buffer.toString().contains("Unclosed quote"));
        buffer.getBuffer().setLength(0);
        cli.execute("filter " + directory + " --from tomorrow", output);
        assertTrue(buffer.toString().contains("Invalid timestamp"));
    }

    @Test
    void reportsUnreadableFilesWithoutCrashingWhenPermissionsCanBeEnforced() throws Exception {
        assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path unreadable = Files.writeString(directory.resolve("blocked.log"),
                "2026-11-21 10:30:15 INFO App - hidden");
        Files.setPosixFilePermissions(unreadable, EnumSet.of(PosixFilePermission.OWNER_WRITE));
        assumeTrue(!Files.isReadable(unreadable), "Current user can bypass file read permissions");
        LogAnalyzerCli cli = new LogAnalyzerCli();
        StringWriter buffer = new StringWriter();

        assertTrue(cli.execute("analyze " + directory, new PrintWriter(buffer)));

        assertTrue(buffer.toString().contains("1 skipped"));
        assertTrue(cli.latestReport().isPresent());
    }
}
