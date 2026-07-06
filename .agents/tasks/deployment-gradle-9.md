---
slug: deployment-gradle-9
branch: renaming
owner: claude
status: blocked
started: 2026-07-06
---

## Goal

Re-enable and migrate the `deployment/server-cloud-run` and
`deployment/simple-server-cloud-run` modules (Jib/Shadow Cloud Run launchers) so they
build under Gradle 9, then include them back in `settings.gradle.kts`.

## Context

- Parked during the CoreJvmCompiler/Gradle-9 migration on branch `renaming`
  (PR [SpineEventEngine/delivery-server#53]); the two `deployment(...)` calls are
  commented out in `settings.gradle.kts`.
- These modules package `server` + `admin-server` (and `simple-server`) into Docker
  images via the Jib and Shadow Gradle plugins. `buildSrc/build.gradle.kts` was already
  bumped to `com.gradleup.shadow:shadow-gradle-plugin:9.4.1` and
  `com.google.cloud.tools:jib-gradle-plugin:3.4.4` (commit `cea7b7f7`), but the module
  build files (`deployment/*/build.gradle.kts`) and their Jib/Shadow config were **not**
  validated under Gradle 9.
- `appClassName = "io.spine.delivery.launcher.Launcher"` (package rename already done,
  commit `a48853e4`).
- **Dependency:** `server-cloud-run` bundles `admin-server`, so
  [admin-server-micronaut-4] must be completed first.

## Plan

- [ ] Validate/adjust the Jib + Shadow configuration in
      `deployment/server-cloud-run/build.gradle.kts` and
      `deployment/simple-server-cloud-run/build.gradle.kts` for Gradle 9 and the new
      plugin coordinates (Shadow moved to `com.gradleup.shadow`; Jib 3.4.x).
- [ ] Confirm `admin-server` is migrated (blocking dependency).
- [ ] Re-enable the two `deployment("...")` calls in `settings.gradle.kts`.
- [ ] `./gradlew :server-cloud-run:build :simple-server-cloud-run:build` green
      (actual image build / `jib` to a registry needs Docker + credentials).

## Log

- 2026-07-06 — recorded; modules parked pending Gradle-9 Jib/Shadow validation.
