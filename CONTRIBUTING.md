# Contributing to Enough

Thanks for helping. Please read the [Code of Conduct](CODE_OF_CONDUCT.md) first.

Enough is a screen-time limiter that is deliberately hard to switch off. Before
proposing a behavior change, read [`docs/requirements.md`](docs/requirements.md),
[`docs/design.md`](docs/design.md), and the [ADRs](docs/adr). Changes that make
the limiter easier to bypass will be declined.

## How changes get in

1. **Open an issue first** for anything bigger than a typo or small fix, so we
   agree on the approach before you spend time.
2. **Fork** the repo and create a branch from `main`
   (`feat/…`, `fix/…`, `docs/…`, `chore/…`).
3. **Open a pull request** against `main` using the PR template.
4. **CI must pass** (`build`: unit tests, lint, debug build). A maintainer must
   approve first-time workflow runs.
5. **The maintainer must approve** before merge. `main` is protected: no direct
   pushes, no force pushes, squash merge only. Resolve all review conversations.

## Local setup

- JDK 17 and the Android SDK.
- Run what CI runs:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Commit and PR style

- [Conventional Commits](https://www.conventionalcommits.org): `feat: …`,
  `fix: …`, `docs: …`, `chore: …`, `refactor: …`, `test: …`.
- One logical change per PR. Keep diffs small and focused.
- Add or update unit tests for behavior changes.
- Update docs or ADRs when you change design decisions.

## Reporting bugs and ideas

Use the [issue templates](../../issues/new/choose). For questions, use
[Discussions](../../discussions). For security problems, see
[SECURITY.md](SECURITY.md) and do **not** open a public issue.

## License

By contributing you agree your work is licensed under the project's
[GPL-3.0](LICENSE) license.
