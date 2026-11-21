# Log Analyzer

Log Analyzer is a Java 21 command-line tool for parsing and inspecting multiple application log files. It recursively reads UTF-8 `.log` files, streams complete entries into analytics or filtering services, and reports severity, logger, source-file, error-hour, and exception-name statistics.

## The problem it solves

Application logs are often spread across files and directories. This tool provides one local workflow for reading the supported log format, attaching continuation lines to their event, summarizing the result, and selecting entries by common criteria without retaining every entry in memory.

## Features

- Strict parsing of timestamps and six severity levels: TRACE, DEBUG, INFO, WARN, ERROR, and FATAL.
- Recursive discovery of lowercase `.log` files, processed in deterministic sorted traversal order.
- UTF-8 input and multiline message/stack-trace continuation handling.
- Counts and percentages by severity; timestamp bounds; ranked loggers and source files.
- ERROR/FATAL counts by logger and hour, plus the peak error hour.
- Heuristic exception/error class-name occurrence counts.
- Streaming filters by severity, message keyword, exact logger name, and inclusive local timestamp bounds.
- Interactive and one-shot CLI commands.

## Technology and prerequisites

- Java 21 or later.
- Apache Maven 3.8 or later for building and testing.
- JUnit 5 for tests.
- No runtime third-party dependencies.

## Project structure

```text
src/main/java/com/ayush/loganalyzer/
├── cli/       CLI entry point, command parser, and output formatting
├── core/      File reading, analysis, aggregation, and filtering services
├── filter/    Immutable filter criteria
├── model/     LogLevel and immutable LogEntry
├── parser/    Single-header parser
└── report/    Immutable AnalysisReport
src/test/java/ JUnit 5 tests
samples/       Example logs, including a nested log directory
docs/          Architecture and implementation limitations
```

## Supported log format

Each event header must use this format:

```text
yyyy-MM-dd HH:mm:ss LEVEL LoggerName - Message
```

For example:

```text
2026-11-21 10:30:15 INFO AuthService - User login successful
```

Severity names are case-insensitive. The timestamp must be a real date and time. The message must contain non-whitespace text.

### Multiline messages

After the first valid header in a file, every following line that is not a valid header is appended to that event, separated by a newline. This includes stack traces and malformed-looking lines. Lines before the first valid header are counted as malformed/orphan lines. The final event is emitted at end-of-file.

## Architecture

`LogFileReader` discovers and reads files a line at a time. `LogParser` recognizes headers and creates `LogEntry` values. `LogAnalyzer` passes entries directly to the incremental `LogAggregator`, which produces an immutable `AnalysisReport`. `LogFilterService` re-reads files and sends matching entries to a consumer. The CLI coordinates these services and formats their results. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Analytics

- Severity percentages use the total number of valid entries; with zero entries every percentage is zero.
- Earliest and latest values use parsed local timestamps and are absent when no valid entries exist.
- Logger and source-file counts are ranked by descending count with deterministic name/path tie-breaking.
- Error totals include ERROR and FATAL. Hourly counts use the entry timestamp truncated to the hour; a tied peak selects the earliest hour.
- Exception statistics count class-like names ending in `Exception` or `Error` in messages and attached continuation lines. This is a heuristic, not a complete stack-trace parser.

## Build and run

Build the executable JAR:

```bash
mvn clean package
```

Launch the interactive application:

```bash
java -jar target/loganalyzer-1.0.0.jar
```

Run one command and exit:

```bash
java -jar target/loganalyzer-1.0.0.jar analyze samples
```

Paths containing spaces can be quoted in interactive commands, or supplied as one shell argument in one-shot mode.

## CLI commands

| Command | Description |
| --- | --- |
| `analyze <folder>` | Analyze `.log` files recursively and display the full report. |
| `summary` | Display the latest successful analysis summary. |
| `errors` | Display ERROR/FATAL totals, logger counts, hourly counts, and peak hour. |
| `top` | Display ranked logger, source-file, and exception-class counts. |
| `filter <folder> [options]` | Stream matching entries from the folder. |
| `help` | Display command usage. |
| `exit` | End the interactive session. |

Filter options can be combined; all supplied criteria must match:

```text
filter <folder> [--severity LEVEL] [--keyword TEXT] [--logger NAME] [--from TIME] [--to TIME]
```

- `--severity` matches one exact severity (case-insensitive when parsed).
- `--keyword` searches only the message, case-insensitively; quote values beginning with `--` or containing spaces.
- `--logger` is an exact logger-name match.
- `--from` and `--to` accept ISO local date-time values such as `2026-11-21T10:30:15`; bounds are inclusive.

Example:

```text
filter samples --severity ERROR --keyword "connection failed" --logger PaymentService --from 2026-11-21T10:00:00 --to 2026-11-21T12:00:00
```

## Example session

```text
loganalyzer> analyze samples
Analysis summary
Valid entries: 8
Files: 2 processed / 2 discovered / 0 skipped
Malformed/orphan lines: 1
Earliest timestamp: 2026-11-21T09:12:03
Latest timestamp: 2026-11-21T10:45:40
Severity counts:
  TRACE 1 (12.50%)
  DEBUG 1 (12.50%)
  INFO  2 (25.00%)
  WARN  1 (12.50%)
  ERROR 2 (25.00%)
  FATAL 1 (12.50%)
Errors (ERROR + FATAL): 3
...
loganalyzer> filter samples --severity ERROR --keyword "database unavailable"
...
Entries examined: 7
Matching entries: 1
loganalyzer> exit
Goodbye.
```

Output paths depend on the working directory and operating system. Sample log fixtures are in [`samples/`](samples/).

## Tests

Run the complete suite:

```bash
mvn clean test
```

Some unreadable-file tests are conditional: they run only where POSIX permissions are supported and the current account cannot bypass them.

## Known limitations

- Only the documented header format is parsed; there is no configurable format detection.
- Timestamps contain no timezone or offset.
- Continuation lines are assigned to the latest valid header, even when they look like malformed headers.
- Files with read failures are skipped; a read failure may occur after earlier entries from that file have already been streamed.
- Filtering performs a new directory scan and file read each time.
- Exception-name detection is heuristic.
- CLI filtering emits matching entries in file traversal order; it does not sort by timestamp.

See [docs/LIMITATIONS.md](docs/LIMITATIONS.md) for detail.

## Future improvements

Potential later work includes configurable log formats, timezone-aware timestamps, structured output formats, richer exception recognition, and more portable unreadable-file integration testing. These are not implemented in v1.0.
