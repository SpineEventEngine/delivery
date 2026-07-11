---
slug: retire-server-based-client
branch: renaming
owner: claude
status: in-progress
started: 2026-07-09
---

## Goal

Retire the server-based `client/client` module from the standalone `client/`
included build (Spine 1.x / Gradle 6), leaving `simple-client` as the sole
Delivery Server client. Update the docs accordingly.

## Context

- Commit `4b7e09a2` removed the Spine-based `server` module and its
  `deployment/server-cloud-run` (which published the
  `gcr.io/spine-dev/message-delivery-server` image). The repo now standardizes on
  the plain-gRPC `simple-server`. That commit deliberately left the `client/`
  build for a separate decision — this task.
- `client/client`'s `DeliveryClient` is the **client-side twin of the deleted
  server**: it uses the Spine `Client` API (`asGuest().select(...)`,
  `asGuest().command(...)`) + `ShardSessionRegistryServiceGrpc`, a protocol only a
  full Spine server answers. `simple-client`'s `SimpleDeliveryClient` uses plain
  gRPC stubs (`InboxServiceGrpc`, `ShardServiceGrpc`) — the `simple-server`
  contract.
- `client/client` is a dead leaf: nothing depends on `:client` (`demo` and
  `integration-test` depend on `:simple-client`); its only test pulled the now-
  orphaned `message-delivery-server` image; its shared fixtures live in `base` /
  `testutil-client` (kept).
- Rejected alternatives: (b) repoint the test at `simple-message-delivery-server`
  — non-viable, the plain-gRPC image doesn't serve Spine queries/commands;
  (c) leave it frozen — means permanently-red, `client/gradlew build` would run a
  test against a deleted image.

## Plan

- [x] `git rm -r client/client/` (8 tracked files, history preserved).
- [x] Drop `include("client")` from `client/settings.gradle.kts`.
- [x] `docs/project.md` — remove the `client` bullet; `simple-client` is now the
      sole client.
- [x] `client/README.md` — repoint the dead `../server` link to `../simple-server`.
- [x] Static sweep clean: no residual `DeliveryClient` /
      `message-delivery-server` / `../server` references outside `build/`.
- [ ] Gradle-level check (`client/gradlew projects`) — NOT runnable in this
      environment (Gradle 6.9.1 + JDK 21 + offline cache miss fails at
      `buildSrc` plugin resolution, before settings evaluation). Needs the
      Spine-1.x toolchain + `read:packages` to confirm; unrelated to this change.
- [ ] User commits (history-safety: agent does not commit).
- [ ] Delete this task doc on merge to master.

## Log

- 2026-07-09 — implemented option (a). Removed `client/client` + its
  `DeliveryClientTest`/`DeliveryBootstrapperTest`, dropped the settings include,
  updated `docs/project.md` and `client/README.md`. Static verification clean;
  Gradle build unavailable locally (Spine 1.x / Gradle 6 build not part of main CI).
