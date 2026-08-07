---
slug: hazelcast-drop-unused-testcontainers
branch: renaming
owner: claude
status: todo
started: 2026-07-06
---

## Goal

Remove the unused Testcontainers test dependencies (and their import) from
`storage/hazelcast/build.gradle.kts`.

## Context

- Surfaced while adopting Testcontainers 2.0.5 for `storage:redis` (commit `5073e43e`,
  PR [SpineEventEngine/delivery-server#53]). See [finalize-corejvm-migration].
- `storage/hazelcast/build.gradle.kts` declares `import io.spine.internal.dependency.Testcontainers`
  plus `testImplementation(Testcontainers.lib)` and `testImplementation(Testcontainers.junitJupiter)`
  (lines 2, 8, 9), but the module's only test — `HazelcastRecordStorageTest` — uses **no**
  Testcontainers API (no `GenericContainer` / `@Container` / `DockerImageName` /
  `@Testcontainers`). Hazelcast runs **embedded / in-process**, so no Docker container is
  needed; that is why the suite passes without Docker.
- The deps are therefore dead weight and, since the repo-wide bump to TC 2.x, they also drag
  TC 2.x's transitive graph (docker-java, JNA, …) onto the hazelcast test classpath for nothing.
- Unlike `storage:redis`, this module needs **no** Docker gate — it is not a Testcontainers
  test. Just drop the deps; do not add `CheckDockerAvailable`/`@RequiresDocker` here.

## Plan

- [ ] Delete the two `testImplementation(Testcontainers.*)` lines and the now-unused
      `import io.spine.internal.dependency.Testcontainers` from
      `storage/hazelcast/build.gradle.kts`.
- [ ] `./gradlew :storage:hazelcast:test` green (needs JDK 17 + `read:packages`; no Docker
      required for this module).
- [ ] Delete this task doc on completion.

## Log

- 2026-07-06 — recorded; minor dead-dependency cleanup deferred from the redis Testcontainers
  work (commit `5073e43e`) so it is not forgotten.
