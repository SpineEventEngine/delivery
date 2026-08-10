---
slug: finalize-corejvm-migration
branch: renaming
owner: claude
status: in-progress
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
- ~~`.github/workflows/pr.yml` and `master.yml` still reference the OLD build.~~ Resolved:
  the repo moved to the workflow set `config` distributes, and the two pre-migration
  files were archived in `9b6c2074` (then deleted — see the CI item in the plan).
- ~~The `client` build is a Spine-1.x / Gradle-6 **included** build.~~ Resolved: folded
  into the main build as root-project modules (see the plan item below).

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

- [x] After admin-server / admin-ui / deployment are migrated, re-enable all in
      `settings.gradle.kts` (remove the parked comments + restore `includeBuild`/`deployment`).
      All three are included again; `deployment-gradle-9.md` is `status: done`.
- [x] Refresh CI. The work turned out to be already done by adopting the workflow set
      that `config` distributes; `pr.yml` and `master.yml` were archived in `9b6c2074`
      and are now deleted. What runs today:
      - `build-on-ubuntu.yml` — `./gradlew build` + `dokkaGenerate` on JDK 17 with
        `actions/checkout@v6` and submodules, plus Codecov. It builds the client
        modules along with everything else, so the retired `build-client` job (which
        ran `cd client && ./gradlew build`) needs no replacement.
      - `publish.yml` — `./gradlew publish -x test` with `GITHUB_TOKEN`/`REPO_SLUG`,
        which covers the `gitHub("delivery-server")` destination. The client artifacts
        publish from the root build, so the retired `publish-client` job (which ran
        `cd client && ./gradlew publish`) needs no replacement either.
      - `build-on-windows-delivery.yml` — the only project-owned workflow; a
        `config:replaces` variant that sets `WINDOWS_CI_NO_DOCKER` so the Redis
        Testcontainers suites skip on Windows runners. This closes item (3) of the
        Testcontainers note above.
      No `read:packages` credential is needed: `buildSrc` ships a read-only PAT, and
      the Docker-gated tests already run on the Ubuntu runner.
      **Still dormant** (parked with their modules, not a CI regression): the container
      build (`:simple-server-cloud-run:jibDockerBuild`), the `:admin-ui:qbuild` job, and
      the App Engine deployment of the demo. The commented-out originals are in
      `9b6c2074^:.github/workflows-archived/master.yml` if they are ever restored.
- [x] Decide + implement the `client` build relationship — folded into the main
      build as root-project modules on Spine v2 (branch `restore-client-modules`,
      see `.agents/tasks/restore-client-modules.md`). The stale `"client"` entry in
      the old `projectsToPublish` referred to the retired `client/client` module
      (`782cd7cf`) — nothing to publish for the `client` container itself.
- [x] Full `./gradlew build` green (with Docker available for the Redis Testcontainers test).
      Confirmed on branch `restore-client-modules`, Docker running, `dokkaGenerate` green
      as well. This also settles item (1) of the Testcontainers note above.
- [ ] Delete the per-module task docs as they complete; delete this one on merge to master.
      `deployment-gradle-9.md` is done and can be archived.

## Log

- 2026-07-06 — recorded; core migration complete (PR #53), finalization pending the
  parked modules.
- 2026-07-06 — Testcontainers/Docker item resolved for `storage:redis`: bumped TC → 2.0.5,
  adapted the two redis tests (`getHost()`, dropped inert `@Container`), and ported
  gcloud-jvm's `CheckDockerAvailable` gate + `@RequiresDocker`/`RequiresDockerCondition`.
  Not yet build-verified (needs Docker + `read:packages`).
- 2026-08-10 — CI item closed. Reviewing it found the refresh already done: the repo runs
  the workflow set `config` distributes, byte-identical to the submodule's copies, and the
  pre-migration `pr.yml`/`master.yml` had been archived out of `.github/workflows/`, where
  GitHub never read them. Deleted that dead `workflows-archived/` directory — its only live
  job duplicated `build-on-ubuntu.yml`, and its commented client jobs invoked the
  `client/gradlew` build that no longer exists. Verified the un-parking and the green
  build at the same time, so the only item left is deleting the task docs on merge.
