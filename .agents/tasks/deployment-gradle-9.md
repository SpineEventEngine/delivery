---
slug: deployment-gradle-9
branch: renaming
owner: claude
status: blocked
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

- [ ] Validate/adjust the Jib + Shadow configuration in
      `deployment/simple-server-cloud-run/build.gradle.kts` for Gradle 9 and the new
      plugin coordinates (Shadow moved to `com.gradleup.shadow`; Jib 3.4.x).
- [ ] Confirm `admin-server` is migrated (blocking dependency).
- [ ] Re-enable the `deployment("simple-server-cloud-run")` call in `settings.gradle.kts`.
- [ ] `./gradlew :simple-server-cloud-run:build` green
      (actual image build / `jib` to a registry needs Docker + credentials).

## Log

- 2026-07-06 — recorded; modules parked pending Gradle-9 Jib/Shadow validation.
- 2026-07-09 — `server-cloud-run` removed with the `server` module; scope narrowed to
  `simple-server-cloud-run`.
