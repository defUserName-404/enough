# ADR-0006: Stack: Kotlin, Compose and DataStore with kotlinx.serialization

Status: Accepted

## Context

The config is one small JSON document.

## Decision

Use Kotlin, Compose and DataStore with kotlinx.serialization. No DI framework: manual constructor injection.

## Consequences

Room would be overkill and is not used.

## Corruption

A corrupt `config.json` must never silently reset to empty (that would fail open and drop every limit). After each successful write and on the first successful read, `ConfigStore` copies a file that decodes to `config.json.bak` (same directory, same encryption). On corruption DataStore's corruption handler restores from that backup; a backup that is missing or also corrupt rethrows the original error, so the app stays fail-closed (error screen, "CONFIG UNREADABLE" notification, existing suspensions kept). A corrupt file never overwrites a good backup.
