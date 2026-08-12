# Fold `testutil-server` into a `testFixtures` source set of `server`

## Goal

Remove the `testutil-server` module, moving its two Java files and three
`.proto` files into `server/src/testFixtures`, so the fixtures are owned by
the module they serve.

## Rationale

`testutil-server` had exactly one dependant — the `server` test source set —
yet carried a full module's overhead: a settings entry, a build file, a Maven
publication with `IncrementGuard`, a `spine-testutil-server` section in the
dependency report, a license report, and a Kover unit.

Gradle's `java-test-fixtures` models this directly. The upstream `spine-server`
module already uses it with Protobuf sources in `src/testFixtures/proto`, and
the Spine compiler creates a `launchTestFixturesSpineCompiler` task without any
extra wiring.

## Steps

- [ ] Apply `` `java-test-fixtures` `` in `server/build.gradle.kts`.
- [ ] Move `testFixturesApi` deps over from `testutil-server/build.gradle.kts`.
- [ ] Drop `testImplementation(project(":testutil-server"))` — the plugin puts
      the fixtures on the test classpath automatically.
- [ ] `git mv` the Java and proto sources into `server/src/testFixtures`.
- [ ] Delete the `testutil-server` directory and its `settings.gradle.kts` entry.
- [ ] Update `docs/project.md`; regenerate the dependency report.

## Verification

`./gradlew clean build` (proto moves require `clean`), with
`:delivery-server:test` green at the same 80 tests, plus `dokkaGenerate`.
`./gradlew projects` must no longer list `:testutil-server`.

## Status

Done — delete this file when the branch merges to master.
