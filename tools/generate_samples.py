#!/usr/bin/env python3
"""Generate deterministic, interconnected synthetic backend logs for Log Analyzer."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
import random


ROOT = Path(__file__).resolve().parents[1]
SAMPLES = ROOT / "samples"
SEED = 20261121
START = datetime(2026, 11, 18, 0, 0, 0)
SEVERITIES = ("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")
SEVERITY_WEIGHTS = (5, 15, 55, 15, 8, 2)


@dataclass(frozen=True)
class LogFile:
    relative_path: str
    service: str
    entries: int
    malformed_prefix: tuple[str, ...] = ()


FILES = (
    LogFile("application/application.log", "Application", 14000, (
        "orphan startup continuation without a header",
        "2026-13-45 99:99:99 ERROR Application - invalid timestamp",
    )),
    LogFile("application/request.log", "RequestRouter", 10000),
    LogFile("application/audit.log", "AuditService", 4500),
    LogFile("services/auth/auth-service.log", "AuthService", 9000, (
        "ERROR AuthService - missing timestamp",
    )),
    LogFile("services/auth/session-manager.log", "SessionManager", 6500),
    LogFile("services/payment/payment-service.log", "PaymentService", 14000, (
        "2026-11-21 10:30:15 UNKNOWN PaymentService - unsupported level",
    )),
    LogFile("services/payment/payment-gateway.log", "PaymentGateway", 9000),
    LogFile("services/payment/refund-service.log", "RefundService", 5000),
    LogFile("services/order/order-service.log", "OrderService", 10000),
    LogFile("services/order/inventory-service.log", "InventoryService", 8000),
    LogFile("services/notification/email-service.log", "EmailService", 6000),
    LogFile("services/notification/sms-service.log", "SmsService", 4000),
    LogFile("services/search/search-service.log", "SearchService", 5500),
    LogFile("services/analytics/analytics-service.log", "AnalyticsService", 6000),
    LogFile("infrastructure/database.log", "DatabasePool", 8500),
    LogFile("infrastructure/cache.log", "CacheService", 6500),
    LogFile("infrastructure/message-broker.log", "MessageBroker", 6500),
    LogFile("infrastructure/scheduler.log", "JobScheduler", 3500),
    LogFile("security/security-events.log", "SecurityMonitor", 4500),
    LogFile("security/access-control.log", "AccessControl", 4000),
    LogFile("archive/day-01/application.log", "Application", 8500),
    LogFile("archive/day-01/payment.log", "PaymentService", 7500),
    LogFile("archive/day-02/application.log", "Application", 8500),
    LogFile("archive/day-02/payment.log", "PaymentService", 7500),
    LogFile("services/low-traffic/health-check.log", "HealthCheck", 350),
    LogFile("services/low-traffic/legacy-adapter.log", "LegacyAdapter", 275),
)


MESSAGES = {
    "Application": [
        ("INFO", "request {request} completed route={route} status={status} durationMs={duration}"),
        ("DEBUG", "correlation {request} linked to order {order}"),
        ("WARN", "request {request} exceeded latency budget route={route} durationMs={duration}"),
        ("ERROR", "request {request} failed while coordinating order {order}"),
    ],
    "RequestRouter": [
        ("TRACE", "route lookup method={method} path={route} request={request}"),
        ("INFO", "dispatched {method} {route} request={request} status={status}"),
        ("DEBUG", "selected upstream {upstream} for request={request}"),
        ("WARN", "retrying upstream {upstream} after timeout request={request}"),
    ],
    "AuditService": [
        ("INFO", "recorded actor={user} action={action} resource={resource}"),
        ("DEBUG", "audit event {request} persisted with retention class=standard"),
        ("WARN", "audit export delayed batch={batch} backlog={count}"),
    ],
    "AuthService": [
        ("INFO", "user {user} login successful request={request}"),
        ("WARN", "invalid credentials for user {user}; attempt={attempt}"),
        ("INFO", "password reset token issued for user {user} request={request}"),
        ("ERROR", "authentication failed for user {user}: com.example.auth.AuthenticationException"),
        ("WARN", "account {user} locked after repeated failed login attempts"),
    ],
    "SessionManager": [
        ("DEBUG", "created session {session} for user {user} request={request}"),
        ("INFO", "session {session} refreshed for user {user}"),
        ("INFO", "session {session} expired after idle timeout"),
        ("WARN", "token validation failed for session {session}: java.lang.IllegalStateException"),
    ],
    "PaymentService": [
        ("INFO", "payment {payment} initiated for order {order} amount={amount} USD"),
        ("INFO", "payment {payment} approved for order {order} request={request}"),
        ("WARN", "payment {payment} retry {attempt} scheduled after gateway timeout"),
        ("ERROR", "payment {payment} failed for order {order}: com.example.payment.PaymentDeclinedException"),
        ("ERROR", "database unavailable while saving payment {payment}: java.sql.SQLException"),
        ("FATAL", "payment worker cannot recover transaction {payment}: java.lang.IllegalStateException"),
        ("INFO", "duplicate transaction {payment} ignored for order {order}"),
    ],
    "PaymentGateway": [
        ("DEBUG", "gateway request sent transaction={payment} provider=Northstar"),
        ("INFO", "gateway authorization completed transaction={payment} response={status}"),
        ("WARN", "gateway timeout for transaction={payment}; retry in {delay}ms"),
        ("ERROR", "gateway read failed transaction={payment}: java.net.SocketTimeoutException"),
        ("ERROR", "connection pool exhausted: java.sql.SQLTransientConnectionException"),
    ],
    "RefundService": [
        ("INFO", "refund {refund} requested for payment {payment} order={order}"),
        ("INFO", "refund {refund} completed for payment {payment}"),
        ("WARN", "refund {refund} waiting for provider confirmation"),
        ("ERROR", "refund provider rejected {refund}: java.io.IOException"),
    ],
    "OrderService": [
        ("INFO", "order {order} created for user {user} request={request}"),
        ("DEBUG", "validated order {order} with {count} line items"),
        ("INFO", "order {order} checkout started payment={payment}"),
        ("INFO", "order {order} completed after inventory and payment confirmation"),
        ("WARN", "order {order} cancelled after payment retry limit"),
        ("ERROR", "order {order} could not reserve stock: com.example.inventory.InsufficientStockException"),
    ],
    "InventoryService": [
        ("INFO", "reserved {count} units sku={sku} order={order}"),
        ("DEBUG", "stock lookup sku={sku} warehouse={warehouse} available={count}"),
        ("WARN", "low stock sku={sku} warehouse={warehouse} remaining={count}"),
        ("ERROR", "reservation conflict order={order}: java.lang.IllegalStateException"),
    ],
    "EmailService": [
        ("INFO", "order confirmation email queued order={order} user={user}"),
        ("INFO", "email delivered message={messageId} provider=Postbird"),
        ("WARN", "email retry {attempt} for message={messageId}: provider timeout"),
        ("ERROR", "email delivery failed message={messageId}: com.example.notification.NotificationDeliveryException"),
    ],
    "SmsService": [
        ("INFO", "SMS delivery confirmed message={messageId} order={order}"),
        ("DEBUG", "SMS provider accepted message={messageId} destination=+1-555-01xx"),
        ("WARN", "SMS retry {attempt} queued after rate limit"),
        ("ERROR", "SMS provider unavailable message={messageId}: java.util.concurrent.TimeoutException"),
    ],
    "SearchService": [
        ("TRACE", "index lookup queryHash={queryHash} shard={shard}"),
        ("INFO", "search completed queryHash={queryHash} results={count} durationMs={duration}"),
        ("WARN", "slow search queryHash={queryHash} durationMs={duration}"),
        ("ERROR", "search index read failed: java.io.IOException"),
    ],
    "AnalyticsService": [
        ("INFO", "aggregated {count} order events batch={batch} window={window}"),
        ("DEBUG", "computed conversion metrics campaign={campaign} batch={batch}"),
        ("WARN", "late event received order={order} latenessMs={duration}"),
        ("ERROR", "aggregation timed out batch={batch}: java.util.concurrent.TimeoutException"),
    ],
    "DatabasePool": [
        ("DEBUG", "connection acquired pool=orders active={count} request={request}"),
        ("TRACE", "query completed statementId={statement} durationMs={duration}"),
        ("INFO", "transaction committed order={order} connection={connection}"),
        ("WARN", "slow query statementId={statement} durationMs={duration}"),
        ("ERROR", "connection acquisition failed: java.sql.SQLTransientConnectionException"),
        ("FATAL", "database pool exhausted after retries: java.sql.SQLException"),
    ],
    "CacheService": [
        ("TRACE", "cache hit key=order:{order} ttlSeconds={ttl}"),
        ("DEBUG", "cache miss key=inventory:{sku} loading from database"),
        ("INFO", "evicted {count} stale cache entries region=orders"),
        ("WARN", "cache refresh delayed key=profile:{user}"),
        ("ERROR", "cache backend timeout key=session:{session}: java.net.SocketTimeoutException"),
    ],
    "MessageBroker": [
        ("DEBUG", "published event OrderPlaced eventId={event} order={order} partition={partition}"),
        ("INFO", "consumer acknowledged eventId={event} group=fulfillment"),
        ("WARN", "consumer lag threshold exceeded group=notifications lag={count}"),
        ("ERROR", "worker failed eventId={event}: java.lang.NullPointerException"),
    ],
    "JobScheduler": [
        ("INFO", "scheduled job {job} started run={run}"),
        ("DEBUG", "job {job} acquired lease owner=worker-{worker}"),
        ("INFO", "scheduled job {job} completed durationMs={duration}"),
        ("WARN", "job {job} delayed because prior run is still active"),
        ("ERROR", "scheduled job {job} failed: java.lang.IllegalStateException"),
    ],
    "SecurityMonitor": [
        ("INFO", "suspicious login pattern user={user} source=198.51.100.{host} request={request}"),
        ("WARN", "rate limit applied client=client-{client} route={route}"),
        ("ERROR", "unauthorized access attempt resource={resource} user={user}"),
        ("FATAL", "security policy engine unavailable: java.lang.IllegalStateException"),
    ],
    "AccessControl": [
        ("DEBUG", "evaluating permission user={user} action={action} resource={resource}"),
        ("INFO", "access granted user={user} action={action} resource={resource}"),
        ("WARN", "permission denied user={user} action={action} resource={resource}"),
        ("ERROR", "policy lookup failed: java.lang.NullPointerException"),
    ],
    "HealthCheck": [
        ("INFO", "readiness probe passed service=application latencyMs={duration}"),
        ("DEBUG", "dependency check passed dependency=database"),
    ],
    "LegacyAdapter": [
        ("INFO", "legacy order payload translated order={order} schemaVersion=2"),
        ("WARN", "legacy client omitted optional field request={request}"),
        ("ERROR", "legacy payload conversion failed: java.lang.IllegalArgumentException"),
    ],
}

STACKS = {
    "java.io.IOException": [
        "    at com.example.storage.BlobStore.read(BlobStore.java:118)",
        "    at com.example.payment.PaymentService.persist(PaymentService.java:204)",
    ],
    "java.net.SocketTimeoutException": [
        "    at com.example.payment.GatewayClient.exchange(GatewayClient.java:142)",
        "    at com.example.payment.PaymentService.charge(PaymentService.java:87)",
        "    at com.example.order.OrderService.checkout(OrderService.java:54)",
    ],
    "java.sql.SQLException": [
        "    at com.example.db.ConnectionPool.acquire(ConnectionPool.java:91)",
        "    at com.example.payment.PaymentRepository.save(PaymentRepository.java:67)",
    ],
    "com.example.payment.PaymentDeclinedException": [
        "    at com.example.payment.PaymentService.authorize(PaymentService.java:131)",
        "    at com.example.order.OrderService.checkout(OrderService.java:62)",
    ],
    "com.example.inventory.InsufficientStockException": [
        "    at com.example.inventory.ReservationService.reserve(ReservationService.java:73)",
        "    at com.example.order.OrderService.create(OrderService.java:109)",
    ],
    "com.example.notification.NotificationDeliveryException": [
        "    at com.example.notification.EmailProvider.send(EmailProvider.java:55)",
        "    at com.example.notification.EmailService.deliver(EmailService.java:122)",
    ],
    "java.util.concurrent.TimeoutException": [
        "    at java.base/java.util.concurrent.FutureTask.get(FutureTask.java:204)",
        "    at com.example.analytics.AggregationWorker.await(AggregationWorker.java:88)",
    ],
}

MALFORMED_LINES = (
    "This line has no timestamp and is an orphan",
    "2026-13-45 99:99:99 ERROR PaymentService - Invalid date",
    "2026-11-21 10:30:15 UNKNOWN PaymentService - Unsupported level",
    "ERROR PaymentService - Missing timestamp",
)


def render_event(rng: random.Random, service: str, index: int, when: datetime) -> tuple[list[str], str]:
    templates = MESSAGES[service]
    level, template = rng.choice(templates)
    # Draw from a global target distribution while retaining service-specific content.
    target_level = rng.choices(SEVERITIES, weights=SEVERITY_WEIGHTS, k=1)[0]
    options = [message for message in templates if message[0] == target_level]
    if options:
        level, template = rng.choice(options)

    values = {
        "request": f"req-{rng.randrange(100000, 999999)}",
        "order": f"ord-{rng.randrange(100000, 999999)}",
        "payment": f"pay-{rng.randrange(100000, 999999)}",
        "refund": f"ref-{rng.randrange(10000, 99999)}",
        "user": f"user-{rng.randrange(1000, 9999)}",
        "session": f"ses-{rng.randrange(100000, 999999)}",
        "amount": f"{rng.randrange(15, 1200)}.{rng.randrange(0, 100):02d}",
        "duration": str(rng.randrange(4, 1800)),
        "status": rng.choice(("200", "202", "204", "declined", "retrying")),
        "route": rng.choice(("/v1/orders", "/v1/payments", "/v1/sessions", "/v1/search")),
        "method": rng.choice(("GET", "POST", "PATCH")),
        "upstream": rng.choice(("orders-api", "payments-api", "identity-api")),
        "attempt": str(rng.randrange(1, 5)),
        "delay": str(rng.choice((250, 500, 1000, 2000))),
        "count": str(rng.randrange(1, 80)),
        "sku": f"SKU-{rng.randrange(1000, 9999)}",
        "warehouse": rng.choice(("east-1", "central-2", "west-1")),
        "messageId": f"msg-{rng.randrange(100000, 999999)}",
        "queryHash": f"{rng.randrange(0x10000, 0xFFFFF):05x}",
        "shard": str(rng.randrange(0, 16)),
        "batch": f"batch-{rng.randrange(100, 999)}",
        "window": rng.choice(("1m", "5m", "15m")),
        "campaign": rng.choice(("winter", "loyalty", "launch")),
        "statement": f"stmt-{rng.randrange(100, 999)}",
        "connection": f"conn-{rng.randrange(1000, 9999)}",
        "ttl": str(rng.randrange(30, 1800)),
        "event": f"evt-{rng.randrange(100000, 999999)}",
        "partition": str(rng.randrange(0, 12)),
        "job": rng.choice(("daily-reconciliation", "stale-session-sweep", "search-reindex")),
        "run": f"run-{rng.randrange(10000, 99999)}",
        "worker": str(rng.randrange(1, 12)),
        "host": str(rng.randrange(1, 254)),
        "client": str(rng.randrange(100, 999)),
        "action": rng.choice(("login", "update", "refund", "export", "read")),
        "resource": f"resource-{rng.randrange(1000, 9999)}",
        "index": str(index),
    }
    message = template.format(**values)

    # Stabilize common terms and ensure error analytics have a clear peak hour.
    if service in {"PaymentService", "PaymentGateway", "DatabasePool"} and index % 37 == 0:
        level = "ERROR"
        message = f"payment retry failed order={values['order']}: java.sql.SQLTransientConnectionException"
    elif service == "PaymentService" and when.date() == (START + timedelta(days=2)).date() and when.hour == 10:
        level = "ERROR"
        message = f"payment retry failed order={values['order']}: java.sql.SQLTransientConnectionException"
    elif index % 521 == 0 and level not in {"ERROR", "FATAL"}:
        message = f"operation completed after retry request={values['request']}"

    lines = [f"{when:%Y-%m-%d %H:%M:%S} {level} {service} - {message}"]
    exception = next((name for name in STACKS if name in message), None)
    if exception and level in {"ERROR", "FATAL"} and rng.random() < 0.72:
        stack = STACKS[exception]
        length = rng.randint(1, len(stack))
        lines.extend(stack[:length])
        if rng.random() < 0.35:
            cause = rng.choice(("java.io.IOException", "java.net.SocketTimeoutException",
                                "java.sql.SQLException", "java.util.concurrent.TimeoutException"))
            lines.append(f"Caused by: {cause}: downstream operation did not complete")
    return lines, level


def main() -> None:
    rng = random.Random(SEED)
    # Remove only generated log fixtures so reruns also remove obsolete prior sample files.
    for old_log in SAMPLES.rglob("*.log"):
        old_log.unlink()

    totals: Counter[str] = Counter()
    physical_lines = 0
    valid_entries = 0
    malformed_lines = 0
    for file_index, spec in enumerate(FILES):
        output_path = SAMPLES / spec.relative_path
        output_path.parent.mkdir(parents=True, exist_ok=True)
        lines: list[str] = list(spec.malformed_prefix)
        malformed_lines += len(spec.malformed_prefix)
        start_offset = timedelta(days=file_index % 3, minutes=(file_index * 7) % 50)
        file_start = START + start_offset
        active_seconds = 3 * 16 * 60 * 60
        step = active_seconds / spec.entries
        previous = file_start
        for index in range(spec.entries):
            # Spread entries across three days of 07:00-23:00 activity; overnight hours stay quiet.
            elapsed = int(index * step + rng.random() * step * 0.25)
            day, within_day = divmod(elapsed, 16 * 60 * 60)
            when = file_start + timedelta(days=day, hours=7, seconds=within_day)
            if when <= previous:
                when = previous + timedelta(seconds=1)
            previous = when
            event_lines, level = render_event(rng, spec.service, index, when)
            lines.extend(event_lines)
            valid_entries += 1
            totals[level] += 1
        output_path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        physical_lines += len(lines)

    print(f"Generated files: {len(FILES)}")
    print(f"Valid log entries: {valid_entries}")
    print(f"Malformed/orphan prefix lines: {malformed_lines}")
    print(f"Total physical lines: {physical_lines}")
    print("Severity distribution:")
    for severity in SEVERITIES:
        count = totals[severity]
        print(f"  {severity}: {count} ({count / valid_entries * 100:.2f}%)")


if __name__ == "__main__":
    main()
