---
status: accepted
date: 2026-08-15
---

# Separate risk facts from consumer idempotency

SmartCareOS stores a normalized device risk fact in `device_risk_event` and stores delivery deduplication separately in `inbox_message`. The risk fact is business evidence; the Inbox is consumer-specific infrastructure and includes a payload fingerprint so that a reused event ID with changed content is rejected instead of silently treated as a duplicate.

This separation costs an additional table and transaction write, but it keeps retry mechanics out of the Device domain and preserves risk evidence independently of the current consumer name. Inbox, risk fact, alarm, transition and Outbox writes share one local transaction; consumers still need tenant-scoped globally stable event IDs.
