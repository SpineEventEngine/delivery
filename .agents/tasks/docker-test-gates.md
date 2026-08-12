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

## Jib blocker — fixed

`jibDockerBuild` failed with `NoSuchMethodError` on
`TarArchiveOutputStream.putArchiveEntry(TarArchiveEntry)`, so the image could not be
built and the integration suites skipped everywhere.

Root cause: `io.spine.tools:intellij-platform` (reached via
`compiler-gradle-plugin` -> `psi-java` -> `psi`) is a 28 MB uber JAR that bundles
`org.apache.commons.compress.**` *without relocating* it, at a version predating
1.26. Its copy shadowed the real `commons-compress:1.26.0` that Jib 3.4.4 compiles
against. Confirmed by loading the class from the buildscript classloader and printing
its code source — not by inference from `buildEnvironment`, which shows a clean 1.26.0.

Fix: declare `CommonsCompress.lib` first on the root buildscript classpath, so the
genuine JAR precedes the uber JAR. Version forcing cannot help here — the offending
classes are not a resolvable dependency, they are inside another artifact.

**Upstream follow-up:** `intellij-platform` should relocate its bundled dependencies.
Until it does, any tool on this classpath needing a post-1.26 commons-compress API
will hit the same wall. Remove the workaround once that lands.

## Status

Done — delete this file when the branch merges to master.
