package com.ayush.loganalyzer.cli;

import com.ayush.loganalyzer.core.LogAnalyzer;
import com.ayush.loganalyzer.core.LogFilterService;
import com.ayush.loganalyzer.report.AnalysisReport;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Testable command dispatcher that coordinates CLI parsing, services, and formatting. */
public final class LogAnalyzerCli {
    private final LogAnalyzer analyzer;
    private final LogFilterService filterService;
    private final CliCommandParser commandParser;
    private final ReportFormatter formatter;
    private AnalysisReport latestReport;

    public LogAnalyzerCli() {
        this(new LogAnalyzer(), new LogFilterService(), new CliCommandParser(), new ReportFormatter());
    }

    LogAnalyzerCli(LogAnalyzer analyzer, LogFilterService filterService,
                   CliCommandParser commandParser, ReportFormatter formatter) {
        this.analyzer = Objects.requireNonNull(analyzer);
        this.filterService = Objects.requireNonNull(filterService);
        this.commandParser = Objects.requireNonNull(commandParser);
        this.formatter = Objects.requireNonNull(formatter);
    }

    /** Executes a command line and returns true while the application should continue. */
    public boolean execute(String input, PrintWriter output) {
        Objects.requireNonNull(output, "output must not be null");
        try {
            List<String> tokens = commandParser.tokenize(input);
            return executeTokens(tokens, output);
        } catch (IllegalArgumentException exception) {
            output.println("Error: " + exception.getMessage());
        } catch (IOException exception) {
            output.println("I/O error: " + exception.getMessage());
        }
        output.flush();
        return true;
    }

    /** Executes already-tokenized arguments, useful for embedding and tests. */
    public boolean executeArguments(List<String> tokens, PrintWriter output) {
        Objects.requireNonNull(output, "output must not be null");
        try {
            return executeTokens(List.copyOf(tokens), output);
        } catch (IllegalArgumentException exception) {
            output.println("Error: " + exception.getMessage());
        } catch (IOException exception) {
            output.println("I/O error: " + exception.getMessage());
        }
        output.flush();
        return true;
    }

    private boolean executeTokens(List<String> tokens, PrintWriter output) throws IOException {
        if (tokens.isEmpty()) {
            return true;
        }
        String command = tokens.getFirst().toLowerCase(java.util.Locale.ROOT);
        List<String> arguments = tokens.subList(1, tokens.size());
        return switch (command) {
                case "analyze" -> analyze(arguments, output);
                case "summary" -> showLatest(arguments, output, formatter::summary);
                case "errors" -> showLatest(arguments, output, formatter::errors);
                case "top" -> showLatest(arguments, output, formatter::top);
                case "filter" -> filter(arguments, output);
                case "help" -> help(arguments, output);
                case "exit" -> exit(arguments, output);
                default -> {
                    output.println("Unknown command: " + command + ". Run help for usage.");
                    yield true;
                }
            };
    }

    /** Returns the latest successful report, if an analysis has completed. */
    public Optional<AnalysisReport> latestReport() {
        return Optional.ofNullable(latestReport);
    }

    private boolean analyze(List<String> arguments, PrintWriter output) throws IOException {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("Usage: analyze <folder>");
        }
        AnalysisReport report = analyzer.analyze(Path.of(arguments.getFirst()));
        latestReport = report;
        output.print(formatter.summary(report));
        output.print(formatter.errors(report));
        output.print(formatter.top(report));
        output.flush();
        return true;
    }

    private boolean filter(List<String> arguments, PrintWriter output) throws IOException {
        CliCommandParser.FilterInvocation invocation = commandParser.parseFilter(arguments);
        var result = filterService.filter(invocation.directory(), invocation.filter(),
                entry -> output.print(formatter.entry(entry)));
        output.print(formatter.filterResult(result));
        output.flush();
        return true;
    }

    private boolean showLatest(List<String> arguments, PrintWriter output,
                               java.util.function.Function<AnalysisReport, String> section) {
        if (!arguments.isEmpty()) {
            throw new IllegalArgumentException("This command does not accept arguments");
        }
        if (latestReport == null) {
            output.println("No analysis available. Run analyze <folder> first.");
        } else {
            output.print(section.apply(latestReport));
        }
        output.flush();
        return true;
    }

    private static boolean help(List<String> arguments, PrintWriter output) {
        if (!arguments.isEmpty()) {
            throw new IllegalArgumentException("Usage: help");
        }
        output.print("Commands:\n"
                + "  analyze <folder>  Analyze all .log files in a folder\n"
                + "  summary           Show the latest analysis summary\n"
                + "  errors            Show ERROR and FATAL statistics\n"
                + "  top               Show ranked loggers, source files, and exception classes\n"
                + "  filter <folder> [--severity LEVEL] [--keyword TEXT] [--logger NAME] [--from TIME] [--to TIME]\n"
                + "                    TIME uses ISO local format, e.g. 2026-11-21T10:30:15\n"
                + "  help              Show this help\n"
                + "  exit              Exit the application\n");
        output.flush();
        return true;
    }

    private static boolean exit(List<String> arguments, PrintWriter output) {
        if (!arguments.isEmpty()) {
            throw new IllegalArgumentException("Usage: exit");
        }
        output.println("Goodbye.");
        output.flush();
        return false;
    }
}
