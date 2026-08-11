Delivery Server client
--------------

This directory hosts the client-side modules of the Delivery Server, backed by
the plain-gRPC [`simple-server`](../simple-server).

The modules are ordinary subprojects of the root Gradle build. The two published
ones carry project names distinct from their directories, so that their Maven
artifacts get the desired IDs (see the root `settings.gradle.kts`):

| Directory | Project | Published artifact |
|---|---|---|
| [`base`](base) | `:client:delivery-client-base` | `io.spine.delivery:spine-delivery-client-base` |
| [`simple-client`](simple-client) | `:client:delivery-client` | `io.spine.delivery:spine-delivery-client` |

The remaining modules are not published:

- [`testutil-client`](testutil-client) — test fixtures for the client modules.
- [`demo`](demo) — a demo "Greeter" Spine application exercising the client.
- [`integration-test`](integration-test) — Testcontainers-based distributed
  tests; tagged `integration` and excluded from the default build.
- [`deployment/demo-appengine-11`](deployment/demo-appengine-11) — the App
  Engine deployment of the demo.

Applications still on Spine 1.x should pin the client artifacts of the `0.14.x`
line (published as `io.spine.delivery:base` and `io.spine.delivery:simple-client`),
which were produced by the former standalone Spine 1.x / Gradle 6 build of this
directory.
