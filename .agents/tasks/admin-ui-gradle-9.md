---
slug: admin-ui-gradle-9
branch: admin-ui-module
owner: claude
status: in-progress
started: 2026-07-06
---

## Goal

Re-enable and migrate the `admin-ui` module (Quasar/Vue web client for the Admin
Service) so its Gradle-wrapped npm/Quasar build works under Gradle 9, then include it
back in `settings.gradle.kts`.

## Context

- Parked during the CoreJvmCompiler/Gradle-9 migration on branch `renaming`
  (PR [SpineEventEngine/delivery-server#53]); commented out in `settings.gradle.kts`
  (`// include("admin-ui")`).
- `admin-ui` is a Quasar (Vue 3 / TypeScript) app built via npm, wrapped in a Gradle
  module (tasks `:admin-ui:qserve`, `:admin-ui:qbuild`). The Gradle ↔ npm integration
  (node/Quasar Gradle plugin) must work under Gradle 9.
- Branding + generated-proto imports were already updated (commit `65ff8e45` rebrand,
  commit `a48853e4` package rename): `admin-ui/src/services/shards.ts` and
  `admin-ui/src/components/ShardListComponent.vue` import from
  `src/gen/spine/delivery/admin/admin_service_pb`; `src/gen/spine/server/delivery/delivery_pb`
  (Spine SDK) is untouched.
- `package.json`: `productName` = `spine-delivery-server-admin-web-client`; the Vue
  toolbar titles read "Delivery Server Admin".

## Plan

- [x] Inspect `admin-ui/build.gradle.kts`; identify the node/Quasar Gradle plugin and
      bump it to a Gradle-9-compatible version (or replace with a maintained node plugin).
      Turned out the node-gradle plugin `com.github.node-gradle.node` 3.5.1 configures
      and runs fine on Gradle 9.6.1 — no bump needed. The real config blocker was the
      fragile `import Build_gradle.{Build,Clean,GenerateTsProto,Serve}` self-imports at
      the top of the script (the synthetic build-script class name). Under Gradle 9's
      Kotlin DSL these top-level task classes resolve by simple name within the same
      script, so the four self-imports were removed.
- [x] Ensure the generated JS/TS protos under `admin-ui/src/gen/...` are regenerated for
      the renamed `spine.delivery.*` packages (the JS proto codegen path), so the
      admin `*_pb` imports resolve. The renamed protos already sit at
      `grpc-api/src/main/proto/spine/delivery/admin/admin_service.proto`; codegen picks
      them up. `buf generate` failed with "symbol already defined" for the Protobuf
      well-known types: `extractIncludeProto` unions protos from every dependency, and
      `protobuf-kotlin` ships the well-known types under a non-standard
      `src/google/protobuf/` prefix while `protobuf-java` provides the canonical
      `google/protobuf/` copies — so buf compiled each well-known type twice. Fixed by
      adding `--exclude-path build/extracted-include-protos/main/src` to the
      `GenerateTsProto` buf invocation (drops the redundant `src/` copies; canonical
      `google/protobuf/` copies remain for import resolution). `model`/`grpc-api` carry
      the same duplicate but never buf-compile the whole tree, so only admin-ui hit it.
- [x] `npm install` and `npx quasar build` succeed; the Gradle `qbuild` task is green.
      Verified from a clean `src/gen`/`dist` with `--rerun-tasks`: node 19.6.0 download,
      npmInstall (487 pkgs), bbgen (buf codegen), and the Quasar production build all
      pass, emitting `dist/spa`. `ShardStatus` / `ShardIndex` resolve from the generated
      `*_pb.ts`.
- [x] Re-enable `include("admin-ui")` in `settings.gradle.kts`.

## Log

- 2026-07-06 — recorded; module parked pending Gradle-9 Quasar/npm integration work.
- 2026-07-14 — migrated on branch `admin-ui-module`; module re-enabled. Root causes were
  the `Build_gradle.*` self-imports (config) and the duplicated well-known protos from
  `protobuf-kotlin` (buf codegen), not the node-gradle plugin version. `:admin-ui:qbuild`
  green from clean; version bumped `0.11.0` -> `0.12.0` for the PR.
