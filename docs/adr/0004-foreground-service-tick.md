# ADR-0004: A foreground service ticks only while the screen is on

Status: Accepted

## Context

WorkManager has a 15-minute minimum, which is too coarse.

## Decision

A foreground service ticks every 15 s when a target app is in the foreground and every 60 s otherwise. It pauses on `SCREEN_OFF` and ticks right away on `SCREEN_ON`, which also handles the midnight rollover, so no exact alarm is needed.

## Consequences

No exact alarm is needed. WorkManager is not used.
