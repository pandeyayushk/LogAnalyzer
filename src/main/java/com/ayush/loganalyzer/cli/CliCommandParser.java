package com.ayush.loganalyzer.cli;

import com.ayush.loganalyzer.filter.LogFilter;
import com.ayush.loganalyzer.model.LogLevel;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tokenizes CLI input and parses filter command arguments. */
public final class CliCommandParser {
    private static final List<String> OPTIONS = List.of("--severity", "--keyword", "--logger", "--from", "--to");

    /** A parsed filter command's directory and criteria. */
    public record FilterInvocation(Path directory, LogFilter filter) {
    }

    /** Splits a command line into arguments, supporting single and double quotes. */
    public List<String> tokenize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean tokenStarted = false;
        for (char character : input.toCharArray()) {
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                } else {
                    token.append(character);
                }
                tokenStarted = true;
            } else if (character == '\'' || character == '"') {
                quote = character;
                tokenStarted = true;
            } else if (Character.isWhitespace(character)) {
                if (tokenStarted) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    tokenStarted = false;
                }
            } else {
                token.append(character);
                tokenStarted = true;
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed quote in command");
        }
        if (tokenStarted) {
            tokens.add(token.toString());
        }
        return List.copyOf(tokens);
    }

    /** Parses {@code filter <folder> [options]} into a directory and immutable filter. */
    public FilterInvocation parseFilter(List<String> arguments) {
        if (arguments == null) {
            throw new IllegalArgumentException("Filter arguments must not be null");
        }
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("Usage: filter <folder> [--severity LEVEL] [--keyword TEXT] [--logger NAME] [--from TIME] [--to TIME]");
        }
        if (arguments.getFirst().isBlank() || arguments.getFirst().startsWith("--")) {
            throw new IllegalArgumentException("Usage: filter <folder> [--severity LEVEL] [--keyword TEXT] [--logger NAME] [--from TIME] [--to TIME]");
        }

        Map<String, String> options = new HashMap<>();
        for (int index = 1; index < arguments.size(); index++) {
            String option = arguments.get(index);
            if (!OPTIONS.contains(option)) {
                throw new IllegalArgumentException("Unknown filter option: " + option);
            }
            if (index + 1 >= arguments.size() || OPTIONS.contains(arguments.get(index + 1))) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            if (options.putIfAbsent(option, arguments.get(++index)) != null) {
                throw new IllegalArgumentException("Duplicate filter option: " + option);
            }
        }

        LogLevel severity = null;
        if (options.containsKey("--severity")) {
            try {
                severity = LogLevel.fromString(options.get("--severity"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid severity: " + options.get("--severity"), exception);
            }
        }
        LocalDateTime from = parseTime(options.get("--from"), "--from");
        LocalDateTime to = parseTime(options.get("--to"), "--to");
        LogFilter filter = new LogFilter(severity, options.get("--keyword"), options.get("--logger"), from, to);
        return new FilterInvocation(Path.of(arguments.getFirst()), filter);
    }

    private static LocalDateTime parseTime(String value, String option) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid timestamp for " + option + ": " + value, exception);
        }
    }
}
