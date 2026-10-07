# Publish the modules the published artifacts depend on

## Goal

Make the published `spine-delivery-client` and `spine-delivery-server`
artifacts resolvable by consumers.

## Rationale

Up to `0.19.3`, the published POMs declare runtime dependencies on project
modules that were never published:

| Published artifact      | Unpublished dependency (as declared in the POM)                  |
|-------------------------|------------------------------------------------------------------|
| `spine-delivery-client` | `io.spine.delivery:grpc-api`                                     |
| `spine-delivery-server` | `io.spine.delivery:grpc-api`, `:redis`, `:hazelcast`             |

The Redis and Hazelcast modules in turn depend on `storage:base`. A throwaway
consumer project resolving `spine-delivery-client:0.19.3` and
`spine-delivery-server:0.19.3` from the public Artifact Registry fails on
`grpc-api`, `redis`, and `hazelcast`. So an application using the client
cannot resolve its runtime classpath.

The fix publishes the missing modules. `SpinePublishing` derives an artifact
ID as `spine-` plus the project name, and `buildSrc` is distributed by
`config`, so the modules get project names distinct from their directories.
`settings.gradle.kts` already does this for the model, server, and client
modules.

| Directory           | Project path                           | Artifact                           |
|---------------------|----------------------------------------|------------------------------------|
| `grpc-api`          | `:delivery-grpc-api`                   | `spine-delivery-grpc-api`          |
| `storage/base`      | `:storage:delivery-storage-base`       | `spine-delivery-storage-base`      |
| `storage/redis`     | `:storage:delivery-storage-redis`      | `spine-delivery-storage-redis`     |
| `storage/hazelcast` | `:storage:delivery-storage-hazelcast`  | `spine-delivery-storage-hazelcast` |

## Vendored health proto

`grpc-api` carried a copy of `grpc/health/v1/health.proto`, generating classes
into `io.grpc.health.v1`. Publishing `grpc-api` would ship those classes and
duplicate the ones in `io.grpc:grpc-services` on the classpath of an
application having both. The history records no reason for the copy. Only the
server's `HealthService` and its tests used it, and the admin UI generated
TypeScript from it without using it. So the copy is removed, and the server
depends on `io.grpc:grpc-services` for the same classes.

## Plan

- [x] Rename the projects in `settings.gradle.kts`.
- [x] Add them to `spinePublishing.modules`.
- [x] Update every `project(...)` reference, including the `testArtifacts` ones.
- [x] Update `dockerDependentModules`, which keys on the project name `redis`.
- [x] Update `docs/project.md` and the comments that name the old Gradle paths.
- [x] Replace the vendored health proto with `io.grpc:grpc-services`.
- [x] Verify the generated POMs reference only published coordinates.
- [x] Run `./gradlew clean build dokkaGenerate` (a `.proto` changed).

## Status

Done — delete this file when the branch merges to master.
