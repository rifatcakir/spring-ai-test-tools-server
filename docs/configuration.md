# Configuration

## Server

Every setting can be given three ways: a command-line flag, an environment variable, or a Spring
Boot property (in an `application.properties` next to the jar, or `-D`). Flags win over
environment variables, which win over the defaults.

> **0.2.0 only:** the short flags (`--storage-dir`, `--admin-token`, ...) lost to the environment
> variable for the same setting. That matters in the Docker image, which sets
> `CASSETTE_STORAGE_DIR=/data/cassettes`: `docker run ... --storage-dir=/elsewhere` was ignored.
> With 0.2.0, use the environment variables (`-e CASSETTE_STORAGE_DIR=...`) or the full property
> names (`--cassette.storage-dir=...`), which work in every version. Fixed after 0.2.0.

| Flag | Environment variable | Default | What it does |
|---|---|---|---|
| `--port` | `SERVER_PORT` | `9090` | HTTP port. |
| `--storage-dir` | `CASSETTE_STORAGE_DIR` | `./cassettes` (jar), `/data/cassettes` (image) | Where fixtures are kept, one subdirectory per namespace. Relative paths are relative to the working directory. |
| `--admin-token` | `CASSETTE_ADMIN_TOKEN` | none | Bearer token for the admin API (`/__admin/...`). **Without it the admin API is disabled**: every admin request gets `403`. |
| `--client-token` | `CASSETTE_CLIENT_TOKEN` | none | Bearer token applications must send to the client API (`/api/v1/...`). Without it, the client API is open. |
| `--cassette.namespace-tokens.<ns>=<token>` | | none | A client token for one namespace, replacing `--client-token` for it. Repeat for several namespaces. |
| `--default-mode` | `CASSETTE_DEFAULT_MODE` | `RECORD_OR_REPLAY` | The mode for applications that set none, in namespaces without their own default. One of `RECORD_OR_REPLAY`, `REPLAY_ONLY`, `RECORD_ALWAYS`, `BYPASS`. |
| `--cassette.misses-per-namespace` | `CASSETTE_MISSESPERNAMESPACE` | `100` | How many recent misses `/__admin/namespaces/{ns}/misses` keeps per namespace (in memory). |

Examples:

```bash
java -jar spring-ai-test-tools-server-0.2.0.jar \
  --port=9090 \
  --storage-dir=/data/cassettes \
  --admin-token="$CASSETTE_ADMIN_TOKEN" \
  --default-mode=REPLAY_ONLY \
  --cassette.namespace-tokens.billing="$BILLING_TOKEN"
```

```bash
docker run -p 9090:9090 -v cassettes:/data/cassettes \
  -e CASSETTE_ADMIN_TOKEN=change-me \
  -e CASSETTE_DEFAULT_MODE=REPLAY_ONLY \
  ghcr.io/rifatcakir/spring-ai-test-tools-server:0.2.0
```

Flags can be appended to `docker run` after the image name too, since the image's entrypoint is the
server itself.

**Anything else Spring Boot supports** works the usual way, for example logging:

```bash
java -jar spring-ai-test-tools-server-0.2.0.jar --logging.level.io.github.rifatcakir=DEBUG
```

**JVM options** go before `-jar`, or into `JAVA_TOOL_OPTIONS` for the image:

```bash
docker run -e JAVA_TOOL_OPTIONS="-Xmx256m" ... ghcr.io/rifatcakir/spring-ai-test-tools-server:0.2.0
```

## Application (the library)

These go into the application that uses the server, under `spring.ai.test.vcr`. The library's own
[configuration reference](https://github.com/rifatcakir/spring-ai-test-tools#configuration-reference)
has the rest.

| Property | Default | What it does |
|---|---|---|
| `enabled` | `false` | Turns the library on at all. Required. |
| `store` | `file` | `remote` to use a Cassette Server for every kind of fixture (chat, streaming, tools, embeddings). |
| `remote.url` | none | The server's root URL, e.g. `http://cassette-server:9090`. Required with `store: remote`; startup fails without it. |
| `remote.namespace` | none | This application's namespace. Required with `store: remote`; startup fails without it. |
| `remote.token` | none | Bearer token, if the server was started with `--client-token` or a namespace token. |
| `remote.connect-timeout` | `2s` | How long to wait for a connection. A timeout fails the call. |
| `remote.read-timeout` | `5s` | How long to wait for each response. A timeout fails the call. |
| `mode` | unset | Set it and it always wins. Leave it unset to follow the namespace's default on the server. |
| `embedding.enabled` | `false` | Also record and replay `EmbeddingModel` calls (separately switched on). |
| `embedding.mode` | unset | Like `mode`, for embeddings. |

A typical test-environment profile:

```yaml
# application-qa.yml
spring:
  ai:
    test:
      vcr:
        enabled: true
        store: remote
        remote:
          url: http://cassette-server:9090
          namespace: ${spring.application.name}
          token: ${CASSETTE_TOKEN:}
```

`namespace: ${spring.application.name}` is a convenient way to get one namespace per application;
the library itself never guesses a namespace, so two applications that happen to share a name do
not share fixtures by accident unless the configuration says so.

## Versions

Use the same server version as the library: server 0.2.0 with library 0.2.0. The server refuses
fixtures in a format newer than it knows (`400`, "upgrade the Cassette Server"), so when the
library is upgraded, upgrade the server with it.
