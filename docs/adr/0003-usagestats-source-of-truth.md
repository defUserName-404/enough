# ADR-0003: UsageStatsManager events are the source of truth

Status: Accepted

## Context

`queryUsageStats` aggregates are imprecise at day boundaries.

## Decision

Sum usage from `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` events since midnight. The app keeps no counters of its own.

## Consequences

The app stays reboot- and crash-proof because it stores no usage counters.
