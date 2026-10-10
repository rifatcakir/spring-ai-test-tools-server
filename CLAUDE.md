# CLAUDE.md — spring-ai-test-tools-server

Instructions for any agent working in this repository.

## What this is

The **Cassette Server**: a small HTTP fixture store for the library
[`io.github.rifatcakir:spring-ai-test-tools`](https://github.com/rifatcakir/spring-ai-test-tools)
(`spring.ai.test.vcr.store: remote`). The library still does everything that matters (hashing,
record/replay, tool isolation, streaming, redaction) inside the application; the server only
stores and serves fixture documents per namespace, plus an admin API. It never calls a model.

Design notes: the library's
[`docs/SERVER-PRD.md`](https://github.com/rifatcakir/spring-ai-test-tools/blob/main/docs/SERVER-PRD.md).
User documentation: [`README.md`](README.md) and [`docs/`](docs).

## State (2026-10-10)

- **`0.2.0` released:** the jar on the GitHub Release `v0.2.0`, the image
  `ghcr.io/rifatcakir/spring-ai-test-tools-server:0.2.0` and `:latest` (public, linked to
  this repository). The library `0.2.0` is on Maven Central.
- **`main` is ahead of `0.2.0`** by one fix (`CHANGELOG.md`, `[Unreleased]`): the short flags
  (`--port`, `--storage-dir`, `--admin-token`, `--client-token`, `--default-mode`) now beat
  environment variables (`CassetteServerApplication.expandShortFlags`). In `0.2.0` they lost,
  so `docker run ... --storage-dir=...` was ignored. Releasing it as `0.2.1` is waiting on the
  maintainer; the runbook is [`docs/RELEASING.md`](docs/RELEASING.md).
- Tests: `mvn test` 72, green. CI green on `main`; the end-to-end job runs nightly.

## Layout

| Directory | What |
|---|---|
| `pom.xml` | Aggregator only (`0-SNAPSHOT`, never deployed) |
| `spring-ai-test-tools-server/` | The server. Spring Boot 4.0.0, Jackson 3 (`tools.jackson.*`), Java 21, **no Spring AI**, no dependency on the library. `maven.deploy.skip`: never goes to Maven Central |
| `spring-ai-test-tools-it/` | End-to-end: builds the image, uses the **published** library from Central with real Ollama. `-Pintegration-test` |
| `examples/cassette-server-demo/` | Walkthrough with a stand-in model, no API key |
| `examples/docker-compose/` | Compose file for a shared environment |
| `fixture-contract/` | One library-written fixture per kind; the server must accept them byte for byte. A **copy** of the library repository's `fixture-contract/`: when the library changes a format, copy the new samples here and raise that kind's `latestSchemaVersion` in `FixtureKind` |
| `docs/` | getting-started, how-it-works, configuration, api, operations, troubleshooting, RELEASING |

## Build and test

```bash
mvn test                                  # server unit and web tests, no Docker
mvn -Pintegration-test verify             # plus end to end (Docker, pulls Ollama + llama3.2:1b)
```

Pitfalls already paid for:

- Testcontainers' own build of the multi-stage Dockerfile fails on GitHub runners ("layer does
  not exist"). CI builds the image with `docker build` and passes `-Dcassette.server.image=...`.
- The Dockerfile copies `target/spring-ai-test-tools-server-*.jar` and requires exactly one
  match: run `mvn clean package`, not `package`, after a version change.
- Pin surefire `3.5.4` (3.6.0 ignores `excludedGroups`) and `ollama/ollama:0.24.0` (0.40 stopped
  serving embeddings from `llama3.2:1b`).
- `spring.mvc.formcontent.filter.enabled=false` is required: a `PUT` sent by curl with a form
  content type otherwise has its body consumed before the controller sees it.
- Every OCI label is set in the Dockerfile on purpose; the base image's Ubuntu labels otherwise
  show on the ghcr.io page. Build releases with `--build-arg VERSION=... --build-arg CREATED=...`.

## Rules

- **Stored bytes are never rewritten.** The server validates a fixture (kind, `hash` matches the
  path, known `schemaVersion`) and stores the document exactly as sent. The export zip must stay
  in the library's file layout so it unzips into `src/test/resources`.
- **Writes are atomic** (temp file + move) and serialized per fixture. One instance per storage
  directory; the server is not clustered.
- **First recording wins** (`409` returns the stored document); only `?overwrite=true`, which the
  library sends in `RECORD_ALWAYS`, replaces.
- **The admin API is off without an admin token** (`403`), never open.
- A newer server must accept every older `schemaVersion`. Release the server before a library
  that writes a new format.
- Code style as in the library: tabs, `this.` prefix, `Assert.notNull` on public entry points,
  constructor injection, Slf4j (`CASSETTE HIT/MISS/STORED ...` at `INFO` with a 12-char hash),
  Javadoc that explains why. Tests: JUnit 5 + AssertJ, real HTTP against the running app.
- `docs/api.md` shows responses captured from a running server. When behaviour changes,
  capture them again rather than editing by hand.

## Working agreements with the maintainer

- Work on a branch, push, let CI run, fast-forward `main` once green. No pull request unless
  asked.
- Never publish (GitHub Release, ghcr.io) without being asked in the conversation. The
  maintainer runs `docker login` / `gh auth` themselves; never type a token.
- The maintainer writes in Turkish; answer in Turkish, keep code, commits and docs in English.

Related repositories: the library
[`rifatcakir/spring-ai-test-tools`](https://github.com/rifatcakir/spring-ai-test-tools) (its
`docs/STATUS.md` is the overall status), and the consumer example
[`rifatcakir/spring-ai-test-tools-example`](https://github.com/rifatcakir/spring-ai-test-tools-example)
(`CassetteServerRemoteStoreTest`, `CassetteServerSharedEnvironmentTest` use the published image).
