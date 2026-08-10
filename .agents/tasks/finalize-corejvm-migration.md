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

- **Testcontainers / Docker for `storage:redis` — RESOLVED (implemented on branch `renaming`,
  pending a Docker-enabled build run).** The local `Could not find a valid Docker environment`
  failure came from Testcontainers `1.16.0` (too old to resolve the Docker CLI *context*) plus a
  stale `~/.testcontainers.properties` pin, both hard-wired to `/var/run/docker.sock`, which
  Docker Desktop for Mac no longer creates (its socket is `~/.docker/run/docker.sock`). Aligned
  with `gcloud-jvm` (`/Users/sanders/Projects/Spine/gcloud-jvm`) instead of hacking the socket:
  - Bumped `buildSrc/.../Testcontainers.kt` → **2.0.5** (+ TC-2.x artifact rename
    `junit-jupiter` → `testcontainers-junit-jupiter`). TC 2.x auto-detects the Docker context, so
    no `/var/run/docker.sock` is needed. Flows to `storage:hazelcast` too (harmless — it declares
    the deps but uses no container APIs); the decoupled `client/` build has its own `buildSrc`
    and is untouched.
  - `storage/redis/.../{RedisRecordStorageTest,MultitenantStorageTest}`: `getContainerIpAddress()`
    → `getHost()`; removed the inert `@Container` (no `@Testcontainers` was present) and the now-
    unused `testcontainers-junit-jupiter` test dep.
  - Ported gcloud-jvm's gate into `storage/redis/build.gradle.kts`: a `CheckDockerAvailable` task
    (`docker info` probe; fails loud when Docker is absent; exempts `WINDOWS_CI_NO_DOCKER`) wired
    as `dependsOn` of the test task, plus a `@RequiresDocker` meta-annotation +
    `RequiresDockerCondition` (skips only when `WINDOWS_CI_NO_DOCKER` is set) on both test classes.
  - **Remaining:** (1) run `./gradlew :storage:redis:test` with Docker to confirm (needs the
    `read:packages` token; this env can't compile); (2) one-time local `rm ~/.testcontainers.properties`
    so the stale strategy pin stops forcing `/var/run/docker.sock` even under TC 2.x; (3) optional:
    when a Windows CI job is added, set `WINDOWS_CI_NO_DOCKER: "true"` on it.
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
- [ ] Update `.github/workflows/pr.yml` + `master.yml`: they still target JDK 16/11
      (need 17), old `actions/*@v1/v2` (need `submodules: recursive` for `config` /
      `.agents/shared`), and jobs for parked modules (`:*-cloud-run:jibDockerBuild`,
      `:admin-ui:qbuild`). Add `GITHUB_ACTOR` + a `read:packages` token for Spine SNAPSHOT
      resolution. Docker/Testcontainers needs NO special CI handling: GitHub-hosted
      `ubuntu-latest` ships a running daemon at `/var/run/docker.sock`, so the
      `storage:redis` tests pass there — just keep Docker-gated tests on Linux runners
      (macOS/Windows GitHub runners lack a usable Linux-container Docker daemon).
- [x] Decide + implement the `client` build relationship — folded into the main
      build as root-project modules on Spine v2 (branch `restore-client-modules`,
      see `.agents/tasks/restore-client-modules.md`). The stale `"client"` entry in
      the old `projectsToPublish` referred to the retired `client/client` module
      (`782cd7cf`) — nothing to publish for the `client` container itself.
- [ ] Full `./gradlew build` green (with Docker available for the Redis Testcontainers test).
- [ ] Delete the per-module task docs as they complete; delete this one on merge to master.

## Log

- 2026-07-06 — recorded; core migration complete (PR #53), finalization pending the
  parked modules.
- 2026-07-06 — Testcontainers/Docker item resolved for `storage:redis`: bumped TC → 2.0.5,
  adapted the two redis tests (`getHost()`, dropped inert `@Container`), and ported
  gcloud-jvm's `CheckDockerAvailable` gate + `@RequiresDocker`/`RequiresDockerCondition`.
  Not yet build-verified (needs Docker + `read:packages`).
