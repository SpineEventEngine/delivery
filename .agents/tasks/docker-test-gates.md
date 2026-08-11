# Arrange Docker-backed tests the way `gcloud-jvm` does

## Goal

Stop the Docker-backed suites from silently not running, and hoist the Docker
gate out of `storage/redis` into the root build, as in `gcloud-jvm`.

## Rationale

`client/client` and `client/integration-test` both had an unconditional
`excludeTags("integration")`, so `DeliveryClientTest`, `ConsistencyTest`, and
`DistributedTest` ran nowhere while the build reported success — the exact
"misleading tests passed" failure mode `gcloud-jvm`'s gate exists to prevent.

`gcloud-jvm` grades its gates: Docker missing **fails** the build
(`CheckDockerAvailable`); a missing private resource only **warns**
(`CheckCredentialsAvailable`) and the tests self-skip. Delivery-server needs
both — Docker is mandatory, but the `gcr.io/spine-dev` server image cannot be,
since most developers have no registry access.

## Outcome

- `CheckDockerAvailable` moved to the root build, parameterized by module and
  driven by `dockerDependentModules()`; the ~100-line copy in
  `storage/redis/build.gradle.kts` is gone.
- `CheckDeliveryImageAvailable` added — warns, naming `jibDockerBuild` as the fix.
- `excludeTags("integration")` removed; the suites carry `@RequiresDeliveryImage`
  and skip themselves visibly when the image is absent.

## Known blocker

`./gradlew :delivery-server-cloud-run:jibDockerBuild` currently fails with
`TarArchiveOutputStream.putArchiveEntry(TarArchiveEntry)` — a `commons-compress`
signature clash on the Jib plugin classpath. Verified pre-existing at `7119cd10`,
unrelated to this change. Until it is fixed, the image cannot be built locally,
so the integration suites skip everywhere.

Fixing it means forcing a `commons-compress` version for the Jib plugin, which
`AGENTS.md` reserves for a dedicated dependency-update task.

## Status

Done — delete this file when the branch merges to master.
