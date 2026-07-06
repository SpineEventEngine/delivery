---
slug: admin-server-micronaut-4
branch: renaming
owner: claude
status: blocked
started: 2026-07-06
---

## Goal

Re-enable and migrate the `admin-server` module (the gRPC → HTTP admin bridge) so it
compiles and its tests pass under the modernized build (Gradle 9 / CoreJvmCompiler /
Spine v2.x), then include it back in `settings.gradle.kts`.

## Context

- Parked during the CoreJvmCompiler/Gradle-9 migration on branch `renaming`
  (PR [SpineEventEngine/delivery-server#53]). It is commented out in
  `settings.gradle.kts` (`// include("admin-server")`).
- The rest of the main build is already migrated and green: `model`, `testutil-server`,
  `grpc-api`, `server`, `simple-server`, `storage:base/redis/hazelcast`.
- `admin-server/build.gradle.kts` applies `id("io.micronaut.application") version "3.7.0"`,
  which uses the Gradle-removed `JavaPluginConvention` and fails to configure under Gradle 9.
  `Micronaut.version` (buildSrc `io.spine.internal.dependency.Micronaut`) is `3.8.3`.
- **admin-server was parked BEFORE the logging migration**, so its Java still uses the
  removed `io.spine.logging.Logging` interface (`_debug()`/`_error()`/`_info()` etc.). It
  needs the same migration applied to `server`/`simple-server` in commit `c3874b3b`:
  `implements Logging` → `implements WithLogging`, `_debug()` → `logger().atDebug()`, and
  wrap `.log(fmt, args)` → `.log(() -> format(...))` (add `import static java.lang.String.format;`).
- The package rename (`io.spine.message.delivery` → `io.spine.delivery`) is already done
  for admin-server (commit `a48853e4`); `mainClass`/annotations point at `io.spine.delivery.admin.*`.

## Plan

- [ ] Bump the Micronaut Gradle plugin `io.micronaut.application` 3.7.0 → 4.x
      (Gradle-9-compatible) in `admin-server/build.gradle.kts`.
- [ ] Bump `Micronaut.version` 3.8.3 → 4.x; expect the `javax.*` → `jakarta.*` namespace
      migration across admin-server sources (DI, HTTP annotations, `@Singleton`, etc.).
- [ ] Migrate admin-server logging to `WithLogging` + `logger().atX().log(() -> format(...))`,
      per commit `c3874b3b`.
- [ ] Fix any remaining Spine v2.x API drift the build surfaces (cross-reference the
      fixes made to `server`/`simple-server`: `DefaultMode`, `vBuild()`→`build()`,
      `StorageFactory.isOpen()`, etc.).
- [ ] Re-enable `include("admin-server")` in `settings.gradle.kts`.
- [ ] `./gradlew :admin-server:build` green. Build env: `JAVA_HOME` = JDK 17 (or 21),
      `GITHUB_ACTOR` + `GITHUB_TOKEN` with `read:packages`.

## Log

- 2026-07-06 — recorded; module parked in `settings.gradle.kts` pending this migration.
