---
slug: artifact-registry-image-path
branch: fix-publishing
owner: claude
status: in-progress
started: 2026-09-07
issue: https://github.com/SpineEventEngine/delivery/issues/52
---

## Goal

Stop naming the Delivery server image by the legacy Container Registry path
`gcr.io/spine-dev/delivery-server`. Publish to, probe for, and document the
Artifact Registry path
`europe-docker.pkg.dev/spine-event-engine/containers/delivery-server`.

## Rationale

- Container Registry stopped accepting writes on 2025-03-18; `gcr.io` now only
  redirects to Artifact Registry, with no announced end date.
- No `delivery-server` image was ever pushed anywhere. The registry's newest
  image is `simple-message-delivery-server:v0.9.1` (2023-07). Local copies of
  `gcr.io/spine-dev/delivery-server:latest` come from `jibDockerBuild`.
- The `containers` repository in the `spine-event-engine` project (the org's
  publishing project, home of the Maven `releases`/`snapshots` repositories) was
  created on 2026-09-07 in the `europe` multi-region and made public
  (`allUsers` → `roles/artifactregistry.reader`). Write access comes from the
  project-level `roles/artifactregistry.writer` of `maven-publisher@...`.
- Jib authenticates to `*.pkg.dev` through Application Default Credentials, so
  no Jib-specific auth configuration is needed.
- User decision: the integration suites keep **skipping** when the image is
  absent locally. Pull-on-absence is a follow-up.

## Changes

- `buildSrc/src/main/kotlin/DockerGates.kt` — `DELIVERY_SERVER_IMAGE_NAME`
  (untagged) is the single source; `DELIVERY_SERVER_IMAGE` derives from it. Gate
  warning offers `docker pull` beside `jibDockerBuild`.
- `deployment/cloud-run/build.gradle.kts` — `jib.to.image` uses the `buildSrc`
  constant; the `GCP_PROJECT` lookup is gone (its only consumer was the image
  name, and the image project is now fixed).
- `fixtures` — `DeliveryImage.NAME` and the skip message updated.
- `DeliveryClientTest` Javadoc, `docs/project.md`, `README.md` — describe the
  public Artifact Registry repository instead of a private Container Registry.

## Unblocking `jibDockerBuild` (pulled in, user decision 2026-09-07)

`jib*` depends on `:admin-ui:qbuild`, which had been failing on `master` since the
2026-08-30 `config` pull raised Protobuf to `4.36.0`: `protobuf-java-util` now ships
`google/protobuf/json_options.proto` and `json_enumvalue_options.proto` in
**edition 2024**, and the admin UI's 2023-era `@bufbuild/buf` 1.15 / `protoc-gen-es` 1.1
could not parse them. CI never runs `qbuild`, `bbgen`, or `jib`, so nobody noticed.
Only `protoc-gen-es` 2.x compiles edition 2024 (verified in a scratch dir; 1.10.1 tops
out at edition 2023). The user chose the full migration over excluding the two files:

- `admin-ui/package.json` — `@bufbuild/buf` 1.72.0, `@bufbuild/protobuf` and
  `@bufbuild/protoc-gen-es` 2.14.1; `package-lock.json` refreshed with the
  Gradle-managed Node.
- `admin-ui/buf.gen.yaml` — v2 config (`local: protoc-gen-es`).
- `src/services/shards.ts` — protobuf-es 2.x API: `create(Schema, init)`,
  `fromJson(Schema, json)`, `fromJsonString`, `toJsonString(Schema, msg)`; message
  types imported as types, `*Schema` descriptors as values; `keyOf()` helper.
- `src/components/ShardListComponent.vue` — `import type { ShardIndex }`.
- Generated code under `src/gen` is gitignored, so nothing generated is committed.
- License headers of all four `.vue` files reflowed under 100 columns: the
  2026-09-04 open-sourcing commit made lines 9 and 11 exactly 101 columns, which
  `quasar build` (eslint `max-len`, `errors: true`) rejects — a second pre-existing
  `qbuild` breakage.
- `tsc --noEmit` on project sources passes on TypeScript 4.9.5; `@bufbuild/protobuf`
  2.x's own `.d.ts` uses `Uint8Array<ArrayBuffer>` (TS ≥ 5.7), so a *full* type-check
  including `node_modules` reports lib errors — as it already did before (`@babel`,
  `@quasar` types). Follow-up: bump TypeScript.

## Verification

- `./gradlew build`
- `./gradlew :delivery-server-cloud-run:jibDockerBuild` produces the image under
  the new name; the gate stops warning; `integration` suites run.
- First real push: happens on merge, via `.github/workflows/publish-containers.yml`
  (push to `master`, `workflow_dispatch`; reuses the `maven-publisher` key of
  `publish.yml`, Jib reads it through Application Default Credentials).
  `.github/workflows/build-containers.yml` runs `jibBuildTar` on pull requests so
  the image build cannot break unnoticed again. Both are repo-owned; `Ubuntu CI`
  is shared across repositories and was deliberately left alone.

## Follow-ups

- Issue: pull the published image when none is present locally.
- Issue: bump the admin UI's TypeScript to ≥ 5.7 (protobuf-es 2.x typings).
