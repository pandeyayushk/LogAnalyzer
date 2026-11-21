# Limitations

These limitations describe the current implementation.

- **Fixed header format:** only `yyyy-MM-dd HH:mm:ss LEVEL LoggerName - Message` is recognized. Logger names must be a single non-whitespace token, and the extension match is lowercase `.log`.
- **No timezone:** parsed timestamps use `LocalDateTime`; no offset or timezone is inferred or applied.
- **Continuation rule:** after a valid header, every non-header line belongs to that entry. A malformed-looking line cannot be distinguished from a stack-trace or continuation line. Before the first header, such lines count as orphans.
- **Message buffering:** files are read line by line, but the current event's complete message is accumulated until the next header or EOF. A single very large multiline event can consume substantial memory.
- **Exception detection:** analytics counts identifier-like names ending in `Exception` or `Error`. This can miss nonstandard exception names and may match class-like text that is not an actual thrown exception. It is not a Java stack-trace parser.
- **Re-reading:** each analysis or filter command scans and reads files again. The CLI retains the latest aggregate report, not the log entries.
- **Partial reads:** if a file fails during reading, the file is marked skipped and the reader continues to other files. Entries emitted before the failure cannot be withdrawn.
- **Ordering:** files are processed in deterministic sorted-child traversal order, not globally sorted by timestamp. Filter output follows this traversal and original event order within each file.
- **Symlinks:** directory traversal does not follow symbolic links.
- **Unreadable-file testing:** permission-based tests are conditional on the filesystem and current user. On Windows, or for privileged POSIX users, those integration tests may be skipped.
- **Output format:** reports are terminal-oriented text; there is no JSON/CSV export or configurable report layout.
