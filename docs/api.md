# API reference

Two APIs on one port:

- the **client API** (`/api/v1/...`), which applications use through the library. Open by
  default; needs a bearer token only if the server was started with `--client-token` or a
  namespace token.
- the **admin API** (`/__admin/...`), for people and scripts. Always needs the admin token, and is
  switched off entirely (`403`) when the server was started without `--admin-token`.

Plus `GET /actuator/health` (and `/actuator/health/liveness`, `/actuator/health/readiness`), with
no token.

Every response below was captured from a running 0.2.0 server. Errors are JSON with one field:

```json
{"error":"<what went wrong>"}
```

Path values used throughout: `{ns}` is a namespace (letters, digits, `.`, `_`, `-`, starting with a
letter or digit); `{kind}` is `chat`, `stream`, `embedding` or `tool`; `{hash}` is the fixture's
SHA-256, 64 lowercase hex characters. The examples use the namespace `order-service`, the admin
token `secret`, and `$HASH` for a fixture hash.

---

## Client API

### Get a fixture

```
GET /api/v1/ns/{ns}/{kind}/{hash}
```

`200` with the fixture document exactly as stored, `Content-Type: application/json`.

`404` when there is none (the miss is added to the namespace's [misses](#recent-misses)):

```json
{"error":"no chat fixture b49dfb0687b34226b650e1eeb28a64c96db4df7f94cf505f5bc8c61496c585a0 in namespace order-service"}
```

A stored file the server cannot read as a fixture of that kind is also reported as `404`, so the
application records it again.

### Store a fixture

```
PUT /api/v1/ns/{ns}/{kind}/{hash}
Content-Type: application/json

<the fixture document>
```

```bash
curl -X PUT -H "Content-Type: application/json" --data-binary @$HASH.json \
  http://localhost:9090/api/v1/ns/order-service/chat/$HASH
```

| Status | Meaning |
|---|---|
| `201 Created` | Stored. Empty body. |
| `409 Conflict` | A fixture is already stored for this hash. The body is **the stored document**, which the library keeps; its own is discarded. |
| `400 Bad Request` | The document was refused; see below. Nothing is stored. |

Refusals, verbatim:

| Problem | Response |
|---|---|
| The body's `hash` is not the hash in the path | `{"error":"body hash \"b49dfb...\" does not match the path hash 0000..."}` |
| The document is another kind than the path says | `{"error":"body is not a stream fixture (it looks like a chat fixture)"}` |
| `schemaVersion` newer than this server knows | `{"error":"schemaVersion 7 is newer than this server knows for chat fixtures (latest 6); upgrade the Cassette Server"}` |
| Not JSON, or not a JSON object | `{"error":"body is not well-formed JSON: ..."}` / `{"error":"body is not a JSON object"}` |
| The hash in the path is malformed | `{"error":"invalid hash 'NOT-A-HASH': expected 64 lowercase hex characters"}` |

An unknown `{kind}` is `404`: `{"error":"unknown fixture kind 'movie': chat, stream, embedding or tool"}`.

### Replace a fixture

```
PUT /api/v1/ns/{ns}/{kind}/{hash}?overwrite=true
```

`200 OK` when an existing fixture was replaced, `201 Created` when there was none. The library sends
this only in `RECORD_ALWAYS`, the one mode where the newest recording wins. Same validation as above.

### Namespace mode for clients

```
GET /api/v1/ns/{ns}/config
```

```json
{"namespace":"order-service","mode":"RECORD_OR_REPLAY","source":"server-default"}
```

`source` is `namespace` when an admin set a mode for this namespace, `server-default` otherwise.
Applications that configure no `spring.ai.test.vcr.mode` read this on every call. Works for any
valid namespace name, including ones with nothing stored yet.

---

## Admin API

All admin requests need `Authorization: Bearer <admin token>`:

```bash
curl -H "Authorization: Bearer secret" http://localhost:9090/__admin/namespaces
```

Without the right token: `401` with `{"error":"missing or wrong admin token"}` and
`WWW-Authenticate: Bearer`. On a server started without an admin token: `403` with
`{"error":"the admin API is disabled; start the server with --admin-token to enable it"}`.

### List namespaces

```
GET /__admin/namespaces
```

```json
[{"namespace":"order-service","mode":"RECORD_OR_REPLAY","modeSource":"server-default","fixtures":{"chat":1,"stream":0,"embedding":0,"tool":0}}]
```

Every namespace with fixtures on disk, plus namespaces that only have a mode set so far.

### List fixtures

```
GET /__admin/namespaces/{ns}/fixtures
GET /__admin/namespaces/{ns}/fixtures?kind=chat
```

```json
[{"hash":"b49dfb0687b34226b650e1eeb28a64c96db4df7f94cf505f5bc8c61496c585a0","kind":"chat","recordedAt":"2026-10-10T00:00:00Z","model":"llama3.2:1b","summary":"Capital of France?"}]
```

`summary` is the first user message (chat, stream), the first input (embedding) or
`toolName(arguments)` (tool), cut to 120 characters. Sorted by kind, then hash. An unknown `kind`
is `400`.

### Get one fixture

```
GET /__admin/namespaces/{ns}/fixtures/{kind}/{hash}
```

`200` with the document, or `404`. The same document the client API serves.

### Delete one fixture

```
DELETE /__admin/namespaces/{ns}/fixtures/{kind}/{hash}
```

`204 No Content`, or `404` if there was none. The next application to send that prompt misses and
records it again (or, in `REPLAY_ONLY`, fails loudly). Deleting through `chat` never removes a
`stream` fixture that happens to share the directory.

### Set a namespace's default mode

```
PUT /__admin/namespaces/{ns}/mode
Content-Type: application/json

{"mode":"REPLAY_ONLY"}
```

```json
{"namespace":"order-service","mode":"REPLAY_ONLY","source":"namespace"}
```

`mode` is one of `RECORD_OR_REPLAY`, `REPLAY_ONLY`, `RECORD_ALWAYS`, `BYPASS` (case-insensitive).
Anything else is `400`:

```json
{"error":"body must be {\"mode\": \"...\"} with one of [RECORD_OR_REPLAY, REPLAY_ONLY, RECORD_ALWAYS, BYPASS], got SOMETIMES"}
```

Applies to applications without their own mode, from their next call. Saved to disk.

### Clear a namespace's default mode

```
DELETE /__admin/namespaces/{ns}/mode
```

```json
{"namespace":"order-service","mode":"RECORD_OR_REPLAY","source":"server-default"}
```

The namespace falls back to the server default (`--default-mode`).

### Recent misses

```
GET /__admin/namespaces/{ns}/misses
```

```json
[{"at":"2026-10-10T16:41:28.489834400Z","kind":"chat","hash":"1111...","recordedSince":false},
 {"at":"2026-10-10T16:41:27.241166900Z","kind":"chat","hash":"b49dfb06...","recordedSince":true}]
```

The client-API `404`s for this namespace, newest first, up to 100 (in memory; cleared on restart).
`recordedSince` says whether that fixture exists now. The quickest way to see why an application
failed with `VcrCacheMissException`: its message names the same hash.

### Export a namespace

```
GET /__admin/namespaces/{ns}/export
```

```bash
curl -H "Authorization: Bearer secret" -o fixtures.zip \
  http://localhost:9090/__admin/namespaces/order-service/export
```

`200`, `Content-Type: application/zip`,
`Content-Disposition: attachment; filename="order-service-fixtures.zip"`. Entries are
`llm-cache/{hash}.json`, `llm-cache-embedding/{hash}.json` and `llm-cache-tool/{hash}.json`, with
the stored bytes unchanged: unzip into `src/test/resources`. Exporting an unchanged namespace twice
gives identical bytes. `404` for a namespace that does not exist: `{"error":"no namespace nope"}`.

---

## Health

```
GET /actuator/health             {"groups":["liveness","readiness"],"status":"UP"}
GET /actuator/health/liveness    {"status":"UP"}
GET /actuator/health/readiness   {"status":"UP"}
```

No token. No other actuator endpoints are exposed.
