package com.ayush.loganalyzer.model;

/** Severity levels supported by the log analyzer, ordered from least to most severe. */
public enum LogLevel {
    /** Detailed diagnostic information. */
    TRACE,
    /** Diagnostic information useful while debugging. */
    DEBUG,
    /** An informational event. */
    INFO,
    /** A potentially harmful condition. */
    WARN,
    /** An error that may prevent an operation from completing. */
    ERROR,
    /** A critical error that may cause the application to stop. */
    FATAL;

    /**
     * Parses a severity name without regard to case.
     *
     * @param value severity name to parse
     * @return the matching severity
     * @throws IllegalArgumentException if the value is null, blank, or unknown
     */
    public static LogLevel fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Log level must not be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Log level must not be blank");
        }

        try {
            return LogLevel.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown log level: " + value, exception);
        }
    }
}
