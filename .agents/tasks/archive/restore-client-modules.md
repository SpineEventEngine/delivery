---
slug: restore-client-modules
branch: restore-client-modules
owner: claude
status: in-progress
started: 2026-08-10
---

## Goal

Fold the standalone Spine-1.x / Gradle-6 `client/` build into the root Gradle 9 /
Spine v2 (CoreJvm) build: all `client/*` modules become subprojects of
`delivery-server`, compile against v2, and `simple-client` + `base` publish like
the root modules.

## Context

- The `client` build was decoupled from the composite (`cea7b7f7`) because a
  Gradle-9 composite cannot host a Gradle-6 build. It copied `model` and
  `grpc-api` protos and recompiled them with Spine 1.9 `mc-java`; as root-build
  subprojects the modules depend on `project(":model")` / `project(":grpc-api")`
  directly, so the proto-copy machinery goes away.
- The `client/client` module was retired earlier (`782cd7cf`); the `"client"`
  entry in the old `projectsToPublish` was a stale leftover. Nothing to publish
  for the `client` *container* — it has no sources (same as `storage`).
- v2 API deltas that drive the source migration:
  - `InboxStorage` is now a **class** over `MessageStorage`/`RecordStorage`
    (v1: interface + `AbstractStorage`). `RemoteInboxStorage` becomes a subclass
    overriding the public/protected read/write methods; the package-private
    `removeBatch → deleteAll` path is served by a gRPC-backed delegate
    `RecordStorage` created via a small `StorageFactory` impl (pattern:
    `ReportingStorageFactory`/`ReportingRecordStorage` in `simple-server`).
  - `ShardedWorkRegistry`, `PickUpOutcomeMixin.pickedUp/alreadyPicked`,
    `InboxMessageComparator`, `InboxMessageMixin` are unchanged — `WorkRegistry`
    and `SimpleDeliveryClient` migrate almost verbatim.
  - `io.spine.logging.Logging` (+ Flogger) → `io.spine.logging.WithLogging`,
    `logger().at*().log(() -> ...)`. Flogger is excluded at the root build level.
  - `io.spine.json.Json` → `io.spine.type.Json` (`@file:JvmName` facade).
  - `Preconditions2`, `Durations2`, `Messages.isDefault`, `vBuild()` survive.

## Plan

- [x] Explore old build, new conventions, v2 API deltas.
- [x] `settings.gradle.kts`: include the client modules; per user request the
      published ones are renamed at the settings level —
      `client/base` → `:client:delivery-client-base`,
      `client/simple-client` → `:client:delivery-client` — so the artifacts are
      `spine-delivery-client-base` / `spine-delivery-client`;
      `:demo-appengine-11` maps flat to `client/deployment/demo-appengine-11`.
      A `pluginManagement` block resolves the marker-less App Engine plugin
      from Maven Central.
- [x] Delete `client/{buildSrc,gradle,gradlew,gradlew.bat,settings.gradle.kts,
      gradle.properties,test-artifacts.gradle}` and
      `client/deployment/demo-appengine-8` (user-requested). Also removed
      `client/base/.../WithInboxMessage.java` — duplicate of the one in `model`.
- [x] `client/build.gradle.kts` → source-less container stub (as `storage`);
      module build files rewritten; repo-owned `io.spine.dependency.web.Jetty`
      and `io.spine.dependency.gcloud.AppEnginePlugin` (2.8.5, Gradle-9-capable,
      diverging from config's 2.2.0 pin) added to `buildSrc`.
- [x] Migrate sources. Notable v2 deltas beyond the plan:
      `vBuild()` → `build()` everywhere (user request);
      `Suppliers2.memoize` over Guava (user request);
      `(required)` is invalid on numeric proto fields → `(min).value`;
      `AggregateRepository` takes `<I, A, S>`; `find`/`create` no longer
      overridable → demo's `Random` injection replaced by a static field;
      **event sourcing removed from aggregates** — `@Apply` bodies move into
      the `@Assign` receptor;
      client error handlers moved: `onServerError` → `CommandRequest`,
      `onStreamingError` → the subscription request;
      `SimpleDeliveryClientTest` requires Docker + the private
      `gcr.io/spine-dev` server image → tagged `integration`, excluded from
      the default build (as the `integration-test` module always was).
- [x] Publishing: root `spinePublishing.modules` += `client:delivery-client`,
      `client:delivery-client-base`. `client` container NOT published (the old
      `"client"` entry referred to the retired `client/client`, `782cd7cf`).
- [x] Update `client/README.md`, `client/demo/README.md` (stale link) and
      `docs/project.md`; tick the `client` decision in
      `finalize-corejvm-migration.md`.
- [x] Full `./gradlew build` green (with Docker for the storage tests);
      `dokkaGenerate` green; `spine-delivery-client{,-base}:0.15.0` verified in
      Maven Local (jar, sources, javadoc, html-docs, POM with correct
      inter-module coordinates). Delete this file on merge to master.

## Log

- 2026-08-10 — started; exploration done, plan recorded.
- 2026-08-10 — all modules wired, migrated, and compiling; module-level tests
  of `base`, `simple-client`, `testutil-client` pass; full build pending.
- 2026-08-10 — full `./gradlew build` + `dokkaGenerate` green; publishing
  verified via Maven Local. Done, pending commit and CI-workflow refresh
  (tracked separately in `finalize-corejvm-migration.md`).
