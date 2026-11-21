# Synthetic service log dataset

This deterministic dataset represents **Northstar Commerce**, a fictional online retail backend. It contains 26 UTF-8 `.log` files across application, service, infrastructure, security, and multi-day archive directories.

## Dataset summary

- **Valid entries:** 177,625
- **Malformed/orphan lines:** 4, all before the first valid header in selected files
- **Physical lines:** 189,952, including stack-trace continuation lines
- **Time range:** 2026-11-18 07:00:02 through 2026-11-22 23:47:43
- **Files:** 26 `.log` files, including nested service and archive paths
- **Encoding and format:** UTF-8, using `yyyy-MM-dd HH:mm:ss LEVEL LoggerName - Message`

Severity counts in the generated data:

| Level | Entries | Share |
| --- | ---: | ---: |
| TRACE | 2,037 | 1.15% |
| DEBUG | 20,233 | 11.39% |
| INFO | 104,000 | 58.55% |
| WARN | 30,883 | 17.39% |
| ERROR | 18,689 | 10.52% |
| FATAL | 1,783 | 1.00% |

Distribution is service/template weighted and intentionally not uniform. TRACE and FATAL are less common because fewer services emit them. The 10:00 hour on 2026-11-20 contains a deliberate payment retry burst and is the dataset's peak error hour. Overnight hours are quiet.

## Directory guide

- `application/`: request routing, application coordination, and audit events.
- `services/auth/`: login, credentials, sessions, token validation, and password resets.
- `services/payment/`: payment orchestration, gateway activity, retries, and refunds.
- `services/order/`: order lifecycle and inventory reservation.
- `services/notification/`: email and SMS delivery.
- `services/search/` and `services/analytics/`: query/index activity and event aggregation.
- `services/low-traffic/`: small health-check and legacy-adapter logs.
- `infrastructure/`: database pool, cache, broker, and scheduled jobs.
- `security/`: suspicious logins, rate limits, and access decisions.
- `archive/day-01/` and `archive/day-02/`: older application and payment streams.

Messages use shared synthetic request, order, payment, user, and event identifiers to suggest related operations across services. Stack traces include varied lengths and causes. The dataset includes repeated names such as `java.io.IOException`, `java.net.SocketTimeoutException`, and `java.sql.SQLTransientConnectionException`, plus application exceptions for payment, authentication, inventory, and notification flows.

Malformed examples are limited to four pre-header lines across selected files. Once a valid header appears, the reader treats every non-header line as a continuation, including malformed-looking text. Stack traces therefore attach to the preceding event as the application currently specifies.

## Regeneration

From the repository root, run:

```bash
python tools/generate_samples.py
```

The script uses Python's standard library and fixed seed `20261121`. Its file paths, sizes, severity weights, event templates, and malformed prefixes are configured in `tools/generate_samples.py`. It replaces existing `.log` fixtures under `samples/` and prints the generated counts.

## Useful commands

Build the executable JAR first with `mvn clean package`, then run from the repository root:

```bash
java -jar target/loganalyzer-1.0.0.jar analyze samples
java -jar target/loganalyzer-1.0.0.jar filter samples --severity ERROR
java -jar target/loganalyzer-1.0.0.jar filter samples --severity WARN --keyword login
java -jar target/loganalyzer-1.0.0.jar filter samples --keyword timeout
java -jar target/loganalyzer-1.0.0.jar filter samples --keyword retry --logger PaymentService
java -jar target/loganalyzer-1.0.0.jar filter samples --keyword database --from 2026-11-20T10:00:00 --to 2026-11-20T10:59:59
java -jar target/loganalyzer-1.0.0.jar filter samples --severity FATAL --keyword never-matches
```

For interactive use, launch the JAR without arguments, then use `analyze samples`, `summary`, `errors`, `top`, `filter ...`, and `exit`. The `summary`, `errors`, and `top` commands require an `analyze` earlier in the same interactive process.
