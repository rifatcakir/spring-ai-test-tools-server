# Getting started

From nothing to an application recording and replaying through a Cassette Server, then getting
those recordings back into a project.

## 1. Run the server

Pick one. All three are the same server.

**The jar** (Java 21):

```bash
curl -LO https://github.com/rifatcakir/spring-ai-test-tools-server/releases/download/v0.2.0/spring-ai-test-tools-server-0.2.0.jar
java -jar spring-ai-test-tools-server-0.2.0.jar --port=9090 --storage-dir=./cassettes --admin-token=change-me
```

**Docker:**

```bash
docker run -d --name cassette-server -p 9090:9090 -v cassettes:/data/cassettes \
  -e CASSETTE_ADMIN_TOKEN=change-me ghcr.io/rifatcakir/spring-ai-test-tools-server:0.2.0
```

**Docker Compose**, for an environment that should keep running: see
[`examples/docker-compose`](../examples/docker-compose/compose.yaml) and
[Operations](operations.md).

Check it is up:

```bash
curl http://localhost:9090/actuator/health
```

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

`--admin-token` is optional, but without it the admin API (listing, deleting, export, modes) is
switched off. See [Configuration](configuration.md) for every flag.

## 2. Connect an application

The application needs the library, version 0.2.0 or later, on the classpath of the build you
deploy to the test environment (not the production artifact; see
[Operations: keeping it out of production](operations.md#keeping-it-out-of-production)):

```xml
<dependency>
    <groupId>io.github.rifatcakir</groupId>
    <artifactId>spring-ai-test-tools</artifactId>
    <version>0.2.0</version>
</dependency>
```

and this configuration, for example in that environment's `application-qa.yml`:

```yaml
spring:
  ai:
    test:
      vcr:
        enabled: true
        store: remote
        remote:
          url: http://localhost:9090
          namespace: order-service
```

Nothing else changes: no code, no `base-url` rewiring, the model provider's configuration stays
as it is. At startup the application logs one line so this is never on by accident:

```
WARN ... VCR store=remote: AI calls are recorded and replayed through the Cassette Server at http://localhost:9090 (namespace 'order-service'). This belongs in test environments only, never in production.
```

**The namespace** keeps one application's fixtures apart from another's. Use the application's
name. Applications that should share answers (replicas, or two services that send the same
prompts on purpose) use the same namespace.

## 3. Record and replay

Call the model through `ChatClient` as the application always does. The first call for a prompt
goes to the model and is stored on the server; the application logs:

```
VCR CACHE MISS [72602edbf470] invoking the real model and recording
VCR RECORDED  [72602edbf470] -> http://localhost:9090/api/v1/ns/order-service/chat/72602edbf470...
```

Every later call with the same prompt, from this instance or any other using `order-service`, is
answered by the server:

```
VCR CACHE HIT  [72602edbf470] replaying order-service/chat/72602edbf470...
```

Streaming calls, tool calls (with tool isolation) and `EmbeddingModel` calls work the same way.
"Same prompt" means exactly the same: messages, model options, tool definitions. A single changed
character is a different fixture. That is the point.

See what is stored:

```bash
curl -H "Authorization: Bearer change-me" http://localhost:9090/__admin/namespaces/order-service/fixtures
```

```json
[{"hash":"72602edbf470...","kind":"chat","recordedAt":"2026-10-10T12:04:36.826Z","model":"llama3.2:1b","summary":"What is the capital of France?"}]
```

## 4. Seal the environment

Once the environment has what it needs, stop anything new from reaching the model:

```bash
curl -X PUT -H "Authorization: Bearer change-me" -H "Content-Type: application/json" \
  -d '{"mode":"REPLAY_ONLY"}' http://localhost:9090/__admin/namespaces/order-service/mode
```

Applications that set no `spring.ai.test.vcr.mode` follow this from their next call, without a
restart. An unrecorded prompt now fails with `VcrCacheMissException` instead of calling the
model, and shows up in the server's misses log:

```bash
curl -H "Authorization: Bearer change-me" http://localhost:9090/__admin/namespaces/order-service/misses
```

To re-record everything for a while, set `RECORD_ALWAYS`; to go back to the server default,
`DELETE` the mode. Details: [How it works: modes](how-it-works.md#modes).

## 5. Bring the recordings into a project

Fixtures recorded in an environment should still be reviewed in a pull request. Export the
namespace and unzip it into the project:

```bash
curl -H "Authorization: Bearer change-me" -o fixtures.zip \
  http://localhost:9090/__admin/namespaces/order-service/export
unzip -o fixtures.zip -d src/test/resources
```

The zip contains `llm-cache/`, `llm-cache-embedding/` and `llm-cache-tool/`, exactly the
library's default file layout, so the project's tests replay them with the default
`store: file`.

## 6. Seed a namespace from a project

The reverse works too: a namespace directory on the server has the same layout. The server reads
fixtures from disk on every request, so this works while it runs:

```bash
mkdir -p cassettes/order-service
cp -r src/test/resources/llm-cache           cassettes/order-service/
cp -r src/test/resources/llm-cache-embedding cassettes/order-service/   # if you have them
cp -r src/test/resources/llm-cache-tool      cassettes/order-service/   # if you have them
```

Fixture files must sit directly in those directories, one `{hash}.json` each. If a project keeps
fixtures in subfolders (for example `llm-cache/basics/`, one per test class), copy the files of
the folders you want, not the folders:

```bash
mkdir -p cassettes/order-service/llm-cache
cp src/test/resources/llm-cache/basics/*.json cassettes/order-service/llm-cache/
```

With Docker, copy into the volume instead (`docker cp`, or mount a host directory at
`/data/cassettes`).

## Next

- [How it works](how-it-works.md) for what happens on each call.
- [Operations](operations.md) before running it for a team.
- [Troubleshooting](troubleshooting.md) when something fails loudly, which is by design.
