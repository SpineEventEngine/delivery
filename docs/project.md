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

Role: **application + published libraries** — a multi-module Gradle build whose
`simple-server` and `model` modules are published as Maven artifacts under the
`io.spine.delivery` group (no `spine-` prefix), while the deployment modules
produce runnable Docker images.

The build is split into two parts. The main build targets the current Spine SDK
(`2.0.0-SNAPSHOT`); the [`client`](../client) directory is a separate **included
build** deliberately kept on Spine 1.x / Gradle 6, so that applications still on
Spine 1.x can depend on the server's Protobuf contract without inheriting the
newer Spine runtime.

### Main build

- `model` — the Protobuf domain model shared by the servers: commands, events,
  rejections, the shard-session registry types, and the `LiquorPickUpOutcome`
  bridge type that connects core `2.0.x` servers with core `1.9.x` clients.
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

### `client` included build (Spine 1.x)

- `base` — interfaces and Protos: the grounding parts of the Delivery Server client.
- `simple-client` — the Delivery Server client implementation; talks plain gRPC
  to the `simple-server`.
- `testutil-client` — test fixtures and Protobuf test types for the client modules.
- `demo` — a demo "Greeter" Spine application exercising the client.
- `integration-test` — Testcontainers-based tests running several server instances.
- `deployment/demo-appengine-8`, `deployment/demo-appengine-11` — App Engine
  deployments of the demo.

### Key constraints

- **Public API stability**: consumer applications pin to versions published from
  here, so removals and signature changes to `model`, `grpc-api`, and
  `simple-server` are breaking. Renaming a Protobuf `package` also changes the
  wire-level type URL, so proto, Java, and the `admin-ui` generated code must
  move together.
- **Two Spine generations**: the `client` included build stays on Spine 1.x /
  Gradle 6 on purpose — do not fold it into the main build.
- **Distribution**: `simple-server` ships as a Docker container on the Google
  Container Registry and is deployed via a Terraform module. All server
  configuration is available through environment variables (`PORT`, `USE_REDIS`,
  `REDIS_HOST`, `USE_HAZELCAST`, `MAX_INBOUND_MESSAGE_SIZE`,
  `SHARD_PROCESSING_TIMEOUT`, …).
- **Versioning** follows the Spine SDK policy; the published version lives in
  `version.gradle.kts` as `versionToPublish`.

Read [`.agents/guidelines/jvm-project.md`](../.agents/guidelines/jvm-project.md) for build stack,
coding style, tests, and versioning.
