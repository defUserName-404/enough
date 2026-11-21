# Security Policy

## Supported versions

Only the latest release is supported with security fixes.

## Reporting a vulnerability

**Do not open a public issue.**

Use GitHub private vulnerability reporting:
[Report a vulnerability](../../security/advisories/new).

Include what you found, steps to reproduce, affected version, and impact. You
can expect an initial response within about 7 days. Fixes are released first,
then the advisory is published and credit is given if you want it.

## What counts

Of particular interest, given what Enough does:

- Ways to bypass or disable limits without the 24h loosening wait.
- Ways to remove Device Owner status, uninstall, or stop the enforcement
  service without the intended process.
- Anything that leaks usage data off the device (Enough requests no internet).

Reports about a user deliberately disabling their own device through
`adb`/factory reset are out of scope; that is the documented escape hatch.
