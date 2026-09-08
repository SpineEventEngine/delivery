---
slug: hazelcast-join-config
branch: hazelcast-join-config
owner: claude
status: in-progress
started: 2026-09-08
---

## Goal

Delivery servers started with `USE_HAZELCAST` form one Hazelcast cluster on any host whose
network carries multicast, cloud VMs included, and the docs explain how to opt into cloud
discovery instead.

## Context

- `Ubuntu CI` on master run 34234286707 failed twice in `DistributedTest`. The merge of
  PR #67 published the server image for the first time, so the `@RequiresDeliveryImage`
  suites ran on CI for the first time.
- Hazelcast's bundled defaults enable cloud auto-detection. GitHub-hosted runners are Azure
  VMs, so inside the Testcontainers containers Hazelcast picks Azure discovery, finds no
  credentials, and starts every member standalone. Locally no cloud is detected and multicast
  is used, which is why the suite passes on developer machines.
- Fix: ship `hazelcast.yaml` in `storage:hazelcast` with auto-detection off, multicast on,
  cluster name `delivery`. `HZ_*` overrides and `-Dhazelcast.config` still win.

## Plan

- [x] `storage/hazelcast/src/main/resources/hazelcast.yaml`
- [x] Javadoc of `HazelcastStorageFactory`
- [x] `HazelcastConfigSpec.kt`
- [x] `server/README.md`: "Cluster discovery" section with the cloud-discovery how-to
- [x] `docs/project.md`: storage-modules bullet
- [x] Verify: `:storage:hazelcast:test`, `dokkaGenerate`, `jibDockerBuild`, two-container
      experiment, `ConsistencyTest.doesNotPickUpShard*` smoke test
- [x] Reviewers: `spine-code-review` and `review-docs`, both approve with changes; changes applied
- [ ] PR: version bump via `pre-pr`; `Ubuntu CI` must pass `DistributedTest`

## Log

- 2026-09-08 17:28 — plan approved, files written, verification pending
- 2026-09-08 17:35 — verified: storage tests and Dokka pass; rebuilt image clusters by multicast
  (`Members {size:2}`), `HZ_CLUSTERNAME` override honoured, a mounted file via
  `JAVA_TOOL_OPTIONS=-Dhazelcast.config` switches to tcp-ip; list values do not apply through
  `HZ_*`; `ConsistencyTest.doesNotPickUpShard` 10/10 green with 3-member clusters
- 2026-09-08 17:42 — review findings applied (README item 4 wording, link definition moved,
  terminology aligned, `guarantee` typo); version bump left to the `pre-pr` step
