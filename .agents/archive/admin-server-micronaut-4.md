---
slug: admin-server-micronaut-4
branch: revive-admin-server-module
owner: claude
status: in-progress
started: 2026-07-06
---

## Goal

Re-enable and migrate the `admin-server` module (the gRPC → HTTP admin bridge) so it
compiles and its tests pass under the modernized build (Gradle 9 / CoreJvmCompiler /
Spine v2.x), then include it back in `settings.gradle.kts`.

## Context

- Parked during the CoreJvmCompiler/Gradle-9 migration on branch `renaming`
  (PR [SpineEventEngine/delivery-server#53]). It was commented out in
  `settings.gradle.kts` (`// include("admin-server")`).
- The old `admin-server/build.gradle.kts` applied `id("io.micronaut.application")
  version "3.7.0"`, which uses the Gradle-removed `JavaPluginConvention` and fails
  to configure under Gradle 9. The old buildSrc `Micronaut` dependency object
  (`io.spine.internal.dependency`) was swept away with the `io.spine.dependency.*`
  buildSrc relayout, so it had to be re-created, not just bumped.
- The package rename (`io.spine.message.delivery` → `io.spine.delivery`) was already
  done for admin-server (commit `a48853e4`).

## Plan

- [x] Bump the Micronaut Gradle plugin `io.micronaut.application` 3.7.0 → **4.6.2**.
      NOT 5.x: Gradle 9 support officially starts with plugin 5.0.0, but the 5.x line
      is compiled for JVM 25 and refuses this repo's Java 17 daemon. 4.6.2 (built for
      Gradle 8) configures and runs cleanly on Gradle 9.6.1 in practice.
      Applied via the `micronaut-application` accessor in the repo-owned
      `buildSrc/src/main/kotlin/DsBuildExtensions.kt` (dependency objects cannot be
      referenced under `plugins {}` directly — see `BuildExtensions.kt` docs; that
      file is config-distributed, hence the separate project-specific one).
- [x] Bump the Micronaut framework 3.8.3 → platform **4.10.17** via a new
      `buildSrc/src/main/kotlin/io/spine/dependency/lib/Micronaut.kt`.
- [x] ~~Migrate admin-server logging to `WithLogging`~~ — moot: the module never used
      `io.spine.logging.Logging`. The `javax.*` → `jakarta.*` migration was also
      already in place (Micronaut 3 had switched DI annotations to Jakarta).
- [x] Micronaut 3 → 4 source/config migration:
      - `HttpBasicAuthProvider`: reactive `AuthenticationProvider` (removed shape) →
        imperative `HttpRequestAuthenticationProvider<B>`; drops the Flux plumbing.
      - `ShardInfoSecurityTest`: `@Client` field + unqualified `@Inject` setter no
        longer receives the qualified client (Micronaut 4 injects a host-less default
        → `NoHostException`); replaced with the canonical
        `@Inject @Client(...) HttpClient` field.
      - `micronaut-jackson-databind` added: Micronaut 4 requires an explicit
        `JsonMapper` implementation (was bundled in 3.x).
      - `runtimeOnly("org.yaml:snakeyaml")` added: YAML config support is opt-in
        in Micronaut 4 and the app uses `application.yml`.
- [x] Spine v2.x API drift: `io.spine.json.Json.toCompactJson` →
      `io.spine.type.Json.toCompactJson` (Kotlin extensions in `JsonExts.kt`,
      `@file:JvmName("Json")`; same static signature from Java).
- [x] Reconcile Micronaut's BOM world with this build's `failOnVersionConflict()`:
      - `enforcedPlatform(Micronaut.bom)` on all admin-server configurations;
      - `resolutionStrategy.eachDependency` group alignments (micronaut core /
        reactor / serde / sourcegen, groovy, caffeine, slf4j, grpc-kotlin,
        junit, kotest) — see comments in `admin-server/build.gradle.kts`;
      - Micronaut stripped from `ksp*` configurations: CoreJvmCompiler applies KSP
        to every module, and the Micronaut plugin auto-wires
        `micronaut-inject-kotlin` into KSP configs, colliding with the Spine
        compiler stack (old KSP/KotlinPoet). No Kotlin sources here, so nothing
        is lost.
      - `grpc-all` dropped in favor of `:grpc-api`'s `api` deps + `grpc-netty-shaded`
        (keeps gRPC's Netty shaded, away from the Netty 4.2.x Micronaut manages).
- [x] Re-enable `include("admin-server")` in `settings.gradle.kts`.
- [x] `./gradlew :admin-server:build` green (3/3 tests pass, Checkstyle/PMD/Kover OK).
      Full `./gradlew build` green except `:storage:redis:test`, which requires a
      Docker daemon (unavailable on the dev machine; unrelated to this change).
      Build env: JDK 17 daemon; Spine SNAPSHOT reads need no personal token (buildSrc
      ships a scrambled read-only PAT).

## Follow-ups (pre-PR)

- [x] Bump `version.gradle.kts` (still `0.10.0`, same as master) — the version gate;
      `/pre-pr` or the `version-bumped` skill handles it.
- [x] Commit the regenerated `docs/dependencies/*` reports together with the change.
- [ ] When the repo moves to a JDK 25 daemon, revisit the Micronaut Gradle plugin
      5.x (officially Gradle-9/10 compatible) and Micronaut Platform 5. (see [#56](https://github.com/SpineEventEngine/delivery-server/issues/56))

## Log

- 2026-07-06 — recorded; module parked in `settings.gradle.kts` pending this migration.
- 2026-07-10 — migrated on branch `revive-admin-server-module`; module re-enabled;
  `:admin-server:build` and the full build (minus Docker-gated Redis tests) green.
  Lessons captured in the Plan checkboxes above and as comments in the build script.
  Note: admin-server tests bind fixed port 8484 (the fake `AdminService`); they are
  the only 8484 binder in the build since the simple-server tests moved to
  ephemeral ports (`657417b9`).
