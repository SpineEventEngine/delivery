# Project: delivery-server

## Overview

`delivery-server` (product name **Delivery Server**, formerly codenamed *Liquor*)
is a standalone gRPC service that offloads sharded message delivery for
Spine-based applications. It is deployed on a host separate from the main
application server and acts as a remote `ShardedWorkRegistry` and `Inbox`
storage: application nodes reach it over gRPC to pick up shards and read/modify
inbox messages, instead of contending on a shared database. This lets a Spine
application scale its delivery across nodes while keeping shard coordination in
one place.

The repository ships a gRPC server implementation plus the client libraries and
deployment wrappers needed to run it, so it is both a set of published artifacts
and a set of runnable applications.

## Architecture

Role: **application + published libraries** — a multi-module Gradle build
targeting the current Spine SDK (`2.0.0-SNAPSHOT`). The published Maven
artifacts live under the `io.spine.delivery` group with the standard `spine-`
prefix: `spine-delivery-model`, `spine-simple-server`, `spine-delivery-client`,
and `spine-delivery-client-base`. The deployment modules produce runnable Docker
images and an App Engine application.

### Main build

- `model` (the `:delivery-model` project) — the Protobuf domain model shared by
  the servers and the clients: commands, events, rejections, the shard-session
  registry types, and the `DeliveryPickUpOutcome` type carrying shard pick-up
  results to clients.
- `grpc-api` — the gRPC service contract (`message_delivery.proto`,
  `admin/admin_service.proto`, plus a vendored `grpc.health.v1` service) and the
  supporting stream-observer/admin helper classes.
- `simple-server` — a **plain gRPC** Delivery Server that does not embed Spine,
  built for throughput. Exposes the delivery gRPC API on port `8484` with
  in-memory, Redis, or Hazelcast storage (the last for running several clustered
  instances sharing a single memory space).
- `testutil-server` — test fixtures and Protobuf test types for `simple-server`.
- `admin-server` — a gRPC client that connects to a running Delivery Server and
  re-exposes shard status over HTTP for maintenance and administration.
- `admin-ui` — a Quasar/Vue (TypeScript) web client for the Admin Service; talks
  to the generated Protobuf types, so it is kept in lock-step with the proto
  packages.
- `storage:base`, `storage:redis`, `storage:hazelcast` — the storage SPI and its
  Redis and Hazelcast implementations.
- `deployment/simple-server-cloud-run` — a Cloud Run launcher that starts
  `simple-server` inside one Docker container (built with the Jib and Shadow
  plugins).

### Client modules (the `client` directory)

The two published client modules carry Gradle project names distinct from their
directories, so that their Maven artifacts get the desired IDs (see
`settings.gradle.kts`):

- `client/base` → `:client:delivery-client-base` — the grounding interfaces of
  the Delivery Server client, published as `spine-delivery-client-base`.
- `client/simple-client` → `:client:delivery-client` — the client
  implementation talking plain gRPC to the `simple-server`, published as
  `spine-delivery-client`.
- `client/testutil-client` — test fixtures and Protobuf test types for the
  client modules (not published).
- `client/demo` — a demo "Greeter" Spine application exercising the client.
- `client/integration-test` — Testcontainers-based tests running several server
  instances; tagged `integration` and excluded from the default build.
- `client/deployment/demo-appengine-11` — the App Engine (Java 11 runtime)
  deployment of the demo.

### Key constraints

- **Public API stability**: consumer applications pin to versions published from
  here, so removals and signature changes to `model`, `grpc-api`, and
  `simple-server` are breaking. Renaming a Protobuf `package` also changes the
  wire-level type URL, so proto, Java, and the `admin-ui` generated code must
  move together.
- **Single Spine generation**: since `0.15.0` the client modules are part of
  the main Spine 2.x build. Applications still on Spine 1.x must pin the client
  artifacts of the `0.14.x` line (published as `io.spine.delivery:base` and
  `io.spine.delivery:simple-client`).
- **Distribution**: `simple-server` ships as a Docker container on the Google
  Container Registry and is deployed via a Terraform module. All server
  configuration is available through environment variables (`PORT`, `USE_REDIS`,
  `REDIS_HOST`, `USE_HAZELCAST`, `MAX_INBOUND_MESSAGE_SIZE`,
  `SHARD_PROCESSING_TIMEOUT`, …).
- **Versioning** follows the Spine SDK policy; the published version lives in
  `version.gradle.kts` as `versionToPublish`.

Read [`.agents/guidelines/jvm-project.md`](../.agents/guidelines/jvm-project.md) for build stack,
coding style, tests, and versioning.
