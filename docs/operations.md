# Operations

Running a Cassette Server for a team: one per environment, on an internal network, with its
storage on a volume.

## Docker Compose

[`examples/docker-compose/compose.yaml`](../examples/docker-compose/compose.yaml) is a complete
setup: the published image, a named volume for the fixtures, a health check, and a restart policy.

```bash
cd examples/docker-compose
CASSETTE_ADMIN_TOKEN=change-me docker compose up -d
docker compose ps        # STATUS shows "healthy" after a few seconds
```

It refuses to start without `CASSETTE_ADMIN_TOKEN` set, on purpose. Applications on the same Compose
network reach it at `http://cassette-server:9090`.

## Kubernetes

One replica, a persistent volume, and the two health endpoints as probes:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cassette-server
spec:
  replicas: 1                      # one instance owns the storage; do not scale out
  strategy:
    type: Recreate                 # never two pods on the same volume
  selector:
    matchLabels: { app: cassette-server }
  template:
    metadata:
      labels: { app: cassette-server }
    spec:
      containers:
        - name: cassette-server
          image: ghcr.io/rifatcakir/spring-ai-test-tools-server:0.2.0
          ports:
            - containerPort: 9090
          env:
            - name: CASSETTE_ADMIN_TOKEN
              valueFrom:
                secretKeyRef: { name: cassette-server, key: admin-token }
          volumeMounts:
            - { name: cassettes, mountPath: /data/cassettes }
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 9090 }
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 9090 }
            initialDelaySeconds: 30
          resources:
            requests: { memory: 256Mi, cpu: 100m }
            limits: { memory: 512Mi }
      securityContext:
        fsGroup: 10001             # the image runs as uid 10001
      volumes:
        - name: cassettes
          persistentVolumeClaim: { claimName: cassettes }
---
apiVersion: v1
kind: Service
metadata:
  name: cassette-server
spec:
  selector: { app: cassette-server }
  ports:
    - port: 9090
```

Applications in the cluster then use `spring.ai.test.vcr.remote.url: http://cassette-server:9090`.

## Single instance, on purpose

The server is not clustered. One process owns the storage directory: it serializes writes to the
same fixture in memory and does not lock files across hosts. Run exactly one instance per storage
directory (hence `replicas: 1` and `Recreate` above). Several environments mean several servers, or
one server with a namespace per environment.

Capacity is rarely a concern: every request is one small file read or write, and fixtures are
typically a few kilobytes. Memory needs are those of a small Spring Boot application.

## Storage, backups and moving data

Everything is plain files under the storage directory (`/data/cassettes` in the image):

```
{storage-dir}/{namespace}/llm-cache/{hash}.json
{storage-dir}/{namespace}/llm-cache-embedding/{hash}.json
{storage-dir}/{namespace}/llm-cache-tool/{hash}.json
{storage-dir}/.cassette-server/namespace-modes.json
```

- **Back up** by copying the directory (or snapshotting the volume). Writes go to a temporary file
  that is renamed into place, so a copy taken while the server runs never contains half-written
  fixtures.
- **Move** a namespace to another server by copying its directory.
- **Review** what an environment recorded with the
  [export](api.md#export-a-namespace): a zip in the `src/test/resources` layout, for a pull
  request. A scheduled job that exports a namespace and opens a pull request keeps an environment's
  recordings under review.
- **Clean up** a namespace by deleting its directory, or single fixtures through the
  [admin API](api.md#delete-one-fixture).

There is no expiry: a fixture stays until someone deletes it.

## Security

The server is meant for an **internal test network**. Fixtures contain prompts and model answers.

- **Admin token.** Always set `--admin-token` / `CASSETTE_ADMIN_TOKEN` to a random value. Without
  it the admin API is disabled, not open.
- **Client token.** If applications from outside the team can reach the server, set
  `--client-token`, or a token per namespace (`--cassette.namespace-tokens.<ns>=...`) to keep
  teams' namespaces apart. Applications send it with `spring.ai.test.vcr.remote.token`.
- **No TLS in the server.** Put it behind your usual TLS-terminating proxy or ingress if traffic
  leaves a trusted network. Tokens are bearer tokens, compared in constant time.
- **Redaction happens in the application.** A `VcrFixtureRedactor` in the application removes
  secrets and personal data before a fixture is sent; the server only ever receives what the
  application chose to keep. See the library's
  [redaction docs](https://github.com/rifatcakir/spring-ai-test-tools#redacting-fixture-content-without-touching-the-cache-key).
- **No provider keys.** The server never calls a model and never sees an API key.
- The image runs as a non-root user (uid 10001).

## Keeping it out of production

The library has to be on the runtime classpath of the build deployed to the test environment.
Keep it out of the production artifact, for example with a Maven profile used only for
test-environment builds:

```xml
<profiles>
    <profile>
        <id>test-env</id>
        <dependencies>
            <dependency>
                <groupId>io.github.rifatcakir</groupId>
                <artifactId>spring-ai-test-tools</artifactId>
                <version>0.2.0</version>
            </dependency>
        </dependencies>
    </profile>
</profiles>
```

Even if it did reach production, nothing happens unless `spring.ai.test.vcr.enabled=true`, and
with `store: remote` the application logs a `WARN` line at startup saying so.

## Logs

The server logs every lookup, store and admin action at `INFO`, with a short hash:

```
CASSETTE HIT  [order-service/chat/b49dfb0687b3]
CASSETTE MISS [order-service/chat/111111111111]
CASSETTE STORED   [order-service/chat/b49dfb0687b3]
CASSETTE CONFLICT [order-service/chat/b49dfb0687b3] keeping the existing fixture
CASSETTE REPLACED [order-service/chat/b49dfb0687b3] (overwrite)
CASSETTE refused [order-service/stream/b49dfb0687b3]: body is not a stream fixture (it looks like a chat fixture)
CASSETTE namespace 'order-service' default mode is now REPLAY_ONLY
CASSETTE rejected admin request GET /__admin/namespaces from 10.0.0.12: missing or wrong admin token
```

`--logging.level.io.github.rifatcakir=DEBUG` adds file-level detail.

## Upgrading

Keep the server and the library on the same version. Upgrade the **server first**: a newer library
may write a fixture format the old server refuses (`400`, "upgrade the Cassette Server"), while a
newer server still accepts every older format. Stored fixtures and namespace modes are kept across
upgrades; the storage layout is the library's own and does not change between versions.
