---
status: accepted
date: 2026-08-16
---

# Enforce the tenant boundary before controller dispatch

SmartCareOS resolves tenant-owned URI resources and rejects cross-tenant access before invoking business controllers; request bodies carrying `tenantId` are checked after deserialization but before controller invocation. This duplicates a small amount of resource-location knowledge at the HTTP boundary, but prevents path-based mutations from changing another tenant before a response-time check could detect the violation. Application queries must still become tenant-scoped as the platform evolves beyond the current modular monolith.
