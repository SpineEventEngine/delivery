---
slug: admin-ui-gradle-9
branch: renaming
owner: claude
status: blocked
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

- [ ] Inspect `admin-ui/build.gradle.kts`; identify the node/Quasar Gradle plugin and
      bump it to a Gradle-9-compatible version (or replace with a maintained node plugin).
- [ ] Ensure the generated JS/TS protos under `admin-ui/src/gen/...` are regenerated for
      the renamed `spine.delivery.*` packages (the JS proto codegen path), so the
      admin `*_pb` imports resolve.
- [ ] `npm install` and `npx quasar build` succeed; the Gradle `qbuild` task is green.
- [ ] Re-enable `include("admin-ui")` in `settings.gradle.kts`.

## Log

- 2026-07-06 — recorded; module parked pending Gradle-9 Quasar/npm integration work.
