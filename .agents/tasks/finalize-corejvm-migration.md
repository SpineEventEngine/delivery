---
slug: finalize-corejvm-migration
branch: renaming
owner: claude
status: blocked
started: 2026-07-06
---

## Goal

Finalize the CoreJvmCompiler/Gradle-9 modernization: after the three parked modules are
migrated, un-park everything, refresh CI, settle the Spine-1.x `client` build, and get a
full `./gradlew build` green.

## Context

- Branch `renaming`, PR [SpineEventEngine/delivery-server#53]. All main-build production
  modules are green (`model`, `testutil-server`, `grpc-api`, `server`, `simple-server`,
  `storage:base/redis/hazelcast`). The parked modules are tracked in their own docs:
  [admin-server-micronaut-4], [admin-ui-gradle-9], [deployment-gradle-9].
- `.github/workflows/pr.yml` and `master.yml` still reference the OLD build (Gradle 7.2,
  mc-java) and will fail. They need updating for Gradle 9.5.1 / the new toolchain, plus
  the GitHub Packages `read:packages` credential the build now needs to resolve Spine.
- The `client` build is a Spine-1.x / Gradle-6 **included** build; it was decoupled from
  the composite (removed `includeBuild("client")`, commit `cea7b7f7`) because a Gradle-9
  composite can't host a Gradle-6 build. Decide whether to re-point it at published server
  Protobuf artifacts or leave it standalone (built via `client/gradlew`).

## Deferred decisions surfaced during the migration

- `storage:redis`'s `MultitenantStorageTest` is a Testcontainers (Docker) test — verify it
  in a Docker-enabled environment.
- Error Prone was kept at `2.9.0` with **NullAway dropped** (its last compatible version
  0.9.2 crashes on the logging lambdas under JDK 17+, and the reference repos dropped
  NullAway). Optional: bump Error Prone → `2.36.0` (needs Guava 33.x on the EP classpath).
- `.claude/settings.json` (SessionStart `init-submodules` hook + permission allowlist) was
  intentionally NOT created (blocked by the self-modification guard). Add it if desired —
  content was drafted in the session that opened PR #53.
- Version was bumped `0.9.2 → 0.9.3` (commit in `6a52b038`). Reconsider a minor bump
  (`0.10.0`) given the package/group rename is API-breaking for consumers.
- Code-level `Liquor*` identifiers (proto message `LiquorPickUpOutcome`, classes
  `LiquorShardRegistry`/`LiquorPickUpOutcomes`) were intentionally left (breaking
  proto/API rename) — a separate background task was spawned for that rebrand.

## Plan

- [ ] After admin-server / admin-ui / deployment are migrated, re-enable all in
      `settings.gradle.kts` (remove the parked comments + restore `includeBuild`/`deployment`).
- [ ] Update `.github/workflows/pr.yml` + `master.yml` for Gradle 9.5.1 / the new
      toolchain / `read:packages` credentials.
- [ ] Decide + implement the `client` build relationship (standalone vs re-pointed).
- [ ] Full `./gradlew build` green (with Docker available for the Redis Testcontainers test).
- [ ] Delete the per-module task docs as they complete; delete this one on merge to master.

## Log

- 2026-07-06 — recorded; core migration complete (PR #53), finalization pending the
  parked modules.
