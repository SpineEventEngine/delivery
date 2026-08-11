# Unify the duplicated test fixtures into a top-level `fixtures` module

## Goal

One copy of the shared test fixtures, used by both the client and the server
suites, with the `server` module back to a plain `main`/`test` layout.

## Rationale

`io.spine.delivery.client.given.TestInboxMessages` and
`io.spine.delivery.server.given.TestInboxMessages` were identical apart from the
`package` line and two imports. Their `.proto` files differed in exactly one
line — `java_package`.

Both proto sets declared the same Protobuf package (`spine.test.delivery`), the
same `type_url_prefix`, and the same messages, so `spine.test.delivery.DoSmth`
had **two** Java classes claiming one Spine type URL. They never met on a single
classpath, but any module depending on both would have hit a `KnownTypes`
conflict. Since the proto package was already shared, unifying changed no type
URLs — only Java package names.

## Outcome

- `client/testutil-client` → top-level `fixtures` (`:fixtures`), depending on
  neither client nor server.
- Java packages dropped their client/server segment:
  `io.spine.delivery.given`, `io.spine.test.delivery`.
- `server/src/testFixtures` deleted; `java-test-fixtures` removed from `server`.
- Six test files repointed to the unified imports.

## Note for reviewers

`DeliveryClientTest` (Docker-gated) and the `client/integration-test` suite
(tagged `integration`) do not run in the default build. Their fixture usage is
compile-verified only. To execute them, run the integration suite with Docker
available.

## Status

Done — delete this file when the branch merges to master.
