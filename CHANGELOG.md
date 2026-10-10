# Changelog

The Cassette Server. Released with the same version as the library it serves,
[`io.github.rifatcakir:spring-ai-test-tools`](https://github.com/rifatcakir/spring-ai-test-tools), and meant to be used with that version.

## [Unreleased]

### Fixed

- The short command-line flags (`--port`, `--storage-dir`, `--admin-token`, `--client-token`,
  `--default-mode`) now beat environment variables, as command-line arguments do in Spring Boot.
  In 0.2.0 they only filled placeholders in `application.properties`, so an environment variable
  for the same setting won; in the Docker image, which sets `CASSETTE_STORAGE_DIR`,
  `docker run ... --storage-dir=...` was ignored. Workaround for 0.2.0: the environment variable,
  or the full property name (`--cassette.storage-dir=...`).

### Documentation

- `docs/`: getting started, how it works, configuration, API reference (every response captured
  from a running server), operations (Compose, Kubernetes, backups, security, upgrades) and
  troubleshooting. `examples/docker-compose/compose.yaml`.

## [0.2.0] - 2026-10-10

First release, built in the library repository and moved here afterwards (with its history).

- A standalone fixture store for the library's remote store (`spring.ai.test.vcr.store: remote`):
  an executable jar (Java 21) and an image, `ghcr.io/rifatcakir/spring-ai-test-tools-server`.
- Client API: `GET`/`PUT /api/v1/ns/{ns}/{kind}/{hash}` (`409` keeps the first recording,
  `?overwrite=true` under `RECORD_ALWAYS`), `GET /api/v1/ns/{ns}/config` for the namespace
  default mode.
- Admin API (`/__admin`, admin token required, disabled without one): namespaces, fixtures, delete,
  namespace mode, recent misses, export as a zip in the library's file layout.
- Storage in the library's own layout, byte for byte, with atomic and per-fixture serialized
  writes; an unreadable file is treated as missing.
- Optional bearer token for the client API, globally or per namespace. `/actuator/health`.

The `0.2.0` image was re-pushed on 2026-10-10, a few hours after the first push, from the same
jar: the first push inherited the base image's Ubuntu labels (title, description, version),
which the ghcr.io page showed as this image's. Only the labels changed; the old digest
`sha256:fa8d30cf…` is still in the registry, untagged.
