# Troubleshooting

The library and the server are built to fail **loudly** rather than do something quietly
different: no silent model call when the server is down, no "close enough" answer when a prompt
changed. So most problems arrive as an exception with a message. Find it below.

## In the application

### `CassetteServerException: ... is unreachable`

```
Cassette Server GET http://cassette-server:9090/api/v1/ns/order-service/chat/... (namespace 'order-service')
failed: the server at http://cassette-server:9090 is unreachable (java.net.ConnectException).
Not calling the real model and not falling back to local fixtures: with store=remote an unavailable
server fails the call.
```

The application could not connect. Check that the server runs (`curl .../actuator/health`), that
`spring.ai.test.vcr.remote.url` is right **from where the application runs** (inside a container,
`localhost` is the container itself), and network policies. With `... timed out`, the server was
reachable but slower than `remote.read-timeout` (default `5s`) or `remote.connect-timeout` (`2s`).

The call fails instead of reaching the model on purpose: an environment that silently starts
calling the model whenever the server blips is exactly what this prevents.

### `CassetteServerException: ... answered HTTP 401 ... Check spring.ai.test.vcr.remote.token.`

The server requires a client token (`--client-token`, or a namespace token) and the application
sent none or the wrong one. Set `spring.ai.test.vcr.remote.token`. A namespace token replaces the
global one for that namespace.

### `CassetteServerException: ... answered HTTP 400: ... newer than this server knows ... upgrade the Cassette Server`

The application's library writes a fixture format this server does not know yet. Upgrade the server
to the library's version. (Upgrade servers before libraries; see
[Operations: upgrading](operations.md#upgrading).)

### `VcrCacheMissException: No VCR fixture for this request, and mode is REPLAY_ONLY ...`

The mode is `REPLAY_ONLY` and nothing is stored for this exact request. The message lists the
request's hash, the expected location (the fixture's URL on the server) and the canonical request
that was hashed.

- **Was the mode meant to be `REPLAY_ONLY`?** If the application sets no mode, it came from the
  server: `curl .../api/v1/ns/{ns}/config` shows it and whether it is the namespace's (`source:
  namespace`) or the server default.
- **Was this prompt ever recorded?** Fixtures are matched exactly. A changed system prompt, a
  different model or temperature, a new tool definition, or a timestamp in the prompt all make a new
  hash. `GET /__admin/namespaces/{ns}/misses` lists recent misses with their hashes;
  `GET /__admin/namespaces/{ns}/fixtures` lists what is stored, with each fixture's first user
  message, to compare against.
- **Wrong namespace?** The location in the message names the namespace the application used.
- To record it: set the namespace to `RECORD_OR_REPLAY` for a while (`PUT /__admin/namespaces/{ns}/mode`),
  run the flow once, and set it back.

### Startup fails: `spring.ai.test.vcr.remote.url must be set when spring.ai.test.vcr.store=remote`

`store: remote` needs both `remote.url` and `remote.namespace`; the library never guesses either.

### `CassetteServerException: Cassette Server namespace '...' reports mode '...', which this library version does not know`

The server's namespace default is a mode this library version does not have. Set
`spring.ai.test.vcr.mode` in the application, or set the namespace to one of `RECORD_OR_REPLAY`,
`REPLAY_ONLY`, `RECORD_ALWAYS`, `BYPASS`.

### Log line: `VCR NOT RECORDED [...] another instance recorded ... first`

Not an error. Two instances missed on the same prompt at the same time; the server kept the first
recording (`409`) and this one was discarded. Both callers got a valid answer, and everyone replays
the first one from now on.

### Every call goes to the model, nothing is stored

Check, in order: `spring.ai.test.vcr.enabled: true`; `store: remote` (with `file`, fixtures go to
local directories); the mode is not `BYPASS` (in the application or the namespace); and the
application's startup log has the `VCR store=remote` warning line. A library older than 0.2.0 has no
remote store and silently uses files.

## On the server

### Admin requests return `403` "the admin API is disabled"

The server was started without an admin token. Restart it with `--admin-token=...` or
`CASSETTE_ADMIN_TOKEN`.

### Admin requests return `401`

The `Authorization: Bearer <token>` header is missing or wrong. The client token does not work for
the admin API.

### `--storage-dir` (or another flag) seems ignored in Docker, on 0.2.0

In 0.2.0 the short flags lost to the environment variable for the same setting, and the image sets
`CASSETTE_STORAGE_DIR=/data/cassettes`. Use `-e CASSETTE_STORAGE_DIR=...` (or mount your volume at
`/data/cassettes`), or the full property name `--cassette.storage-dir=...`. Fixed after 0.2.0.

### A fixture I copied in is not found

Fixture files must be directly in `{storage-dir}/{namespace}/llm-cache/` (or `llm-cache-embedding/`,
`llm-cache-tool/`), named `{hash}.json`, not in a subfolder. The file's own `hash` field must match
its name, and its `schemaVersion` must be one the server knows; a file that fails these checks is
treated as missing (and logged as "not a readable ... fixture").

### Fixtures disappear after a container restart

The storage directory is not on a volume. Mount one at `/data/cassettes`
(`-v cassettes:/data/cassettes`).
