# Fixture contract

The library and the Cassette Server share no code. This directory is what they share instead:
one real fixture per family, written by the library, laid out exactly like a server namespace
and like the library's default `src/test/resources` layout.

```
schema-versions.properties     latest schemaVersion per kind
llm-cache/                     one chat and one stream fixture
llm-cache-embedding/           one embedding fixture
llm-cache-tool/                one tool-execution fixture
```

The original lives in the library repository,
[`rifatcakir/spring-ai-test-tools/fixture-contract`](https://github.com/rifatcakir/spring-ai-test-tools/tree/main/fixture-contract), where
the library's `FixtureContractTests` proves the samples are what it writes today. This copy is
what the server's `FixtureContractTests` checks against: every sample accepted on `PUT`, served
back byte for byte on `GET`, and exported unchanged.

When the library changes a fixture format, it regenerates its samples; copy them here in the
same release, and teach `FixtureKind` the new `schemaVersion`. The server test fails until both
are done, which is the point.
