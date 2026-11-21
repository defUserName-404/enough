# ADR-0006: Stack: Kotlin, Compose and DataStore with kotlinx.serialization

Status: Accepted

## Context

The config is one small JSON document.

## Decision

Use Kotlin, Compose and DataStore with kotlinx.serialization. No DI framework: manual constructor injection.

## Consequences

Room would be overkill and is not used.
