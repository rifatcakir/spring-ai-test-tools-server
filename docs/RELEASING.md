# Releasing the Cassette Server

For maintainers. A release is three things with the same version: the jar on a GitHub Release,
the image on ghcr.io (`:X.Y.Z` and `:latest`), and a tag. Nothing goes to Maven Central
(`maven.deploy.skip` is set in the server's pom).

The server's version follows the library's: server `X.Y` serves library `X.Y`. A patch release
of the server alone (for example `0.2.1`, a server fix) still works with library `0.2.0`, since
the fixture formats did not change; say so in the release notes.

## 1. Prepare on a branch

- Set `<version>` in `spring-ai-test-tools-server/pom.xml`.
- Move `CHANGELOG.md`'s `[Unreleased]` entries under `[X.Y.Z] - YYYY-MM-DD`.
- Update the version in the user-facing download and image references:

  ```bash
  grep -rn "0\.2\.0" --include=*.md --include=*.yaml . | grep -v CHANGELOG
  ```

  Only references that tell people what to download or run change (README quick start,
  `docs/getting-started.md`, `docs/configuration.md`, `docs/operations.md`,
  `examples/docker-compose/compose.yaml`). Statements about a past version stay as they are,
  for example the `0.2.0` caveats in `docs/configuration.md` and `docs/troubleshooting.md`.
- Bump the published library version in `spring-ai-test-tools-it/pom.xml` and
  `examples/cassette-server-demo/pom.xml` only when a matching library release exists on
  Central.

## 2. Verify

```bash
mvn clean test
mvn -pl spring-ai-test-tools-server clean package -DskipTests
docker build --build-arg VERSION=X.Y.Z --build-arg CREATED=$(date -u +%Y-%m-%dT%H:%M:%SZ) \
  -t ghcr.io/rifatcakir/spring-ai-test-tools-server:X.Y.Z spring-ai-test-tools-server
mvn -Pintegration-test verify -Dcassette.server.image=ghcr.io/rifatcakir/spring-ai-test-tools-server:X.Y.Z
```

Then a smoke test of both distributions:

```bash
java -jar spring-ai-test-tools-server/target/spring-ai-test-tools-server-X.Y.Z.jar --port=9191 --admin-token=t &
curl -s localhost:9191/actuator/health                       # {"status":"UP",...}
docker run --rm -d -p 9192:9090 -e CASSETTE_ADMIN_TOKEN=t ghcr.io/rifatcakir/spring-ai-test-tools-server:X.Y.Z
curl -s localhost:9192/actuator/health
docker inspect --format '{{json .Config.Labels}}' ghcr.io/rifatcakir/spring-ai-test-tools-server:X.Y.Z
```

The labels must name the Cassette Server and version `X.Y.Z`, not Ubuntu. Push the branch, let
CI pass, fast-forward `main`.

## 3. Publish (the maintainer logs in)

```bash
docker login ghcr.io -u rifatcakir                          # a token with write:packages
docker tag ghcr.io/rifatcakir/spring-ai-test-tools-server:X.Y.Z ghcr.io/rifatcakir/spring-ai-test-tools-server:latest
docker push ghcr.io/rifatcakir/spring-ai-test-tools-server:X.Y.Z
docker push ghcr.io/rifatcakir/spring-ai-test-tools-server:latest

gh release create vX.Y.Z spring-ai-test-tools-server/target/spring-ai-test-tools-server-X.Y.Z.jar \
  --repo rifatcakir/spring-ai-test-tools-server --target main \
  --title "X.Y.Z" --notes-file release-notes.md
```

`release-notes.md`: the CHANGELOG section, the `java -jar` and `docker run` lines, and which
library version it serves.

## 4. Afterwards

- Check the package page on ghcr.io shows the right description and stays public.
- `curl -LO` the jar from the release URL in the README and start it once.
- Update the example project
  ([`spring-ai-test-tools-example`](https://github.com/rifatcakir/spring-ai-test-tools-example))
  if its tests pin the image version, and the server version in the library's README if it
  names one.
