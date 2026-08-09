---
slug: deployment-gradle-9
branch: simple-server-cloud-run
owner: claude
status: done
started: 2026-07-06
---

## Goal

Re-enable and migrate the `deployment/simple-server-cloud-run` module (Jib/Shadow
Cloud Run launcher) so it builds under Gradle 9, then include it back in
`settings.gradle.kts`.

## Context

- Parked during the CoreJvmCompiler/Gradle-9 migration on branch `renaming`
  (PR [SpineEventEngine/delivery-server#53]); the `deployment(...)` call is
  commented out in `settings.gradle.kts`.
- The `server-cloud-run` sibling was removed together with the `server` module
  (the repo standardized on `simple-server`).
- This module packages `simple-server` + `admin-server` + `admin-ui` into a Docker
  image via the Jib and Shadow Gradle plugins. `buildSrc/build.gradle.kts` was already
  bumped to `com.gradleup.shadow:shadow-gradle-plugin:9.4.1` and
  `com.google.cloud.tools:jib-gradle-plugin:3.4.4` (commit `cea7b7f7`), but the module
  build files (`deployment/*/build.gradle.kts`) and their Jib/Shadow config were **not**
  validated under Gradle 9.
- `appClassName = "io.spine.delivery.launcher.Launcher"` (package rename already done,
  commit `a48853e4`).
- **Dependency:** `simple-server-cloud-run` bundles `admin-server` + `admin-ui`, so
  [admin-server-micronaut-4] must be completed first.

## Plan

- [x] Validate/adjust the Jib + Shadow configuration in
      `deployment/simple-server-cloud-run/build.gradle.kts` for Gradle 9 and the new
      plugin coordinates (Shadow moved to `com.gradleup.shadow`; Jib 3.4.x).
- [x] Confirm `admin-server` is migrated (blocking dependency).
- [x] Re-enable the `deployment("simple-server-cloud-run")` call in `settings.gradle.kts`.
- [x] `./gradlew :simple-server-cloud-run:build` green
      (actual image build / `jib` to a registry needs Docker + credentials).

## Log

- 2026-07-06 — recorded; modules parked pending Gradle-9 Jib/Shadow validation.
- 2026-07-09 — `server-cloud-run` removed with the `server` module; scope narrowed to
  `simple-server-cloud-run`.
- 2026-08-09 — unblocked: `admin-server` revived on Micronaut 4 (`aef81892`). Module
  migrated and re-enabled; full `./gradlew build` green (fat JAR + `jib*` tasks OK):
  - Shadow applied by the new ID `com.gradleup.shadow` (9.4.1 from the `buildSrc`
    classpath); Jib version now lives in repo-owned `Jib.kt` + a `jib` accessor in
    `DsBuildExtensions.kt` — the earlier `buildSrc/build.gradle.kts` bump (`cea7b7f7`)
    was config-distributed and got overwritten by `./config/pull` (`45c7ff18`).
  - `io.spine.internal.*` imports migrated to `io.spine.dependency.*`;
    `prepareExtras` (gone from `buildSrc`) replaced with inline `providers`-based
    `GCP_PROJECT` + git-hash values; `mainClassName` convention property replaced
    with `application.mainClass`.
  - Micronaut alignment extracted from `admin-server` into shared
    `alignMicronautPlatform()` (`MicronautAlignment.kt`); the launcher classpath also
    settles the cross-graph conflicts (Netty/Reactor/RxJava per the Micronaut platform
    pins, Log4j2 2.26.0, SnakeYAML 2.5, Hazelcast 5.7.0 via new `Hazelcast.kt`).
  - `Launcher.java` migrated off the removed `io.spine.logging.Logging` to
    `WithLogging`; the old `Log4j2.slf4jBridge` replaced with repo-owned
    `Log4j2Bridge.slf4j2` (`log4j-slf4j2-impl`).
  - `jib`-to-registry still unverified (needs Docker + credentials), as scoped.
