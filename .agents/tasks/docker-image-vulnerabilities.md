---
slug: docker-image-vulnerabilities
branch: claude/docker-image-vulnerabilities-c1dc2b
owner: claude
status: in-progress
started: 2026-09-08
---

## Goal

Apply the fixes Artifact Registry reports as available for the published
Delivery server image
`europe-docker.pkg.dev/spine-event-engine/containers/delivery-server`
(digest `sha256:f1c335ce…`, the first `latest` push of 2026-09-08).

## Findings (150 occurrences)

| Source | Findings | Fix available | Where it lives |
|---|---|---|---|
| Maven, Netty `codec-http` 4.2.16 | 1 | 4.2.17.Final | `Micronaut.nettyVersion` pin |
| Maven, Jackson 2.21.2 / 3.1.2 | 21 | 2.21.5 / 3.1.5 | shaded inside `hazelcast-5.7.0.jar` |
| Go stdlib 1.26.5 (`/usr/bin/pebble`) | 10 | 1.26.6 | Ubuntu 26.04 base layer |
| Ubuntu packages | 118 | 31 of them | `eclipse-temurin:17-jre` base |

## What this repo can fix

- `buildSrc/.../lib/Micronaut.kt` — `nettyVersion` 4.2.16.Final → 4.2.17.Final.
  The Micronaut Platform 4.10.17 (then the newest 4.x) pins 4.2.16; the launcher's
  `eachDependency` rule forces the pin onto the whole image classpath, so the
  launcher must override the platform. The config-owned `Netty.kt` is already
  at 4.2.17, which restores the documented "equal to the catalog" invariant.

## What it cannot fix yet

- **Hazelcast 5.7.0 (2026-05-13) is the newest release**; no 5.7.1 exists on
  Maven Central or GitHub. The Jackson copies are relocated inside its JAR, so
  no Gradle rule can replace them. Hazelcast tracks the advisories in
  [hazelcast/hazelcast#26597](https://github.com/hazelcast/hazelcast/issues/26597)
  (`jackson-databind`) and
  [hazelcast/hazelcast#26623](https://github.com/hazelcast/hazelcast/issues/26623)
  (`jackson-core`); a `hazelcast-slim` JAR without the shaded copies is proposed
  in [hazelcast/hazelcast#26603](https://github.com/hazelcast/hazelcast/pull/26603).
  Re-check Hazelcast releases.
- **Base image**: the published amd64 image shares every base layer with the
  current `eclipse-temurin:17-jre` tag (compared 2026-09-08), so a rebuild today
  changes nothing. Jib resolves the tag afresh on each `master` push, so the
  next Temurin rebuild flows in with the next publish. The 87 findings without
  an Ubuntu fix, plus `curl`, `wget`, `perl`, `gnupg`, are packages the JRE
  does not need; a slimmer base (Temurin Alpine, or distroless Java 17) would
  remove them, but that is a deployment decision, not an available fix.

## Verification

- `:delivery-server-cloud-run` runtime classpath resolves every `io.netty`
  artifact at 4.2.17.Final.
- Build passes; dependency reports regenerated.

## Netty 4.2.19.Final (2026-10-14, version 0.23.2)

Netty [4.2.18.Final][netty-4.2.18] (2026-09-09, 30 advisories) and
[4.2.19.Final][netty-4.2.19] (2026-10-06, 16 advisories) are security releases.
The 0.23.1 image ships Netty 4.2.17.Final. Micronaut (the framework behind the
Admin server's HTTP port) and Redisson (the Redis client) both depend on Netty,
and the launcher pins its version. The fixes in `netty-codec-http` for HTTP/1
request smuggling and resource exhaustion apply to the Admin server.

- `Micronaut.nettyVersion` 4.2.17.Final → 4.2.19.Final, with its KDoc updated.
  The launcher's `eachDependency` rule forces the pin onto every `io.netty`
  artifact of the image except `netty-tcnative-*`. The pin overrode the
  platform's 4.2.16.Final (Micronaut Platform 4.10.17) until the platform update
  below made the two equal.
- `buildSrc/.../lib/Netty.kt` 4.2.17.Final → 4.2.19.Final. This keeps the
  catalog equal to `Micronaut.nettyVersion`. The file belongs to `config`:
  `config` must carry the same value, or `./config/pull` reverts it. `config`'s
  `master` already carries it (`f19612dc`, "Refresh external dependencies",
  2026-10-14). `./config/pull` moves `config` to its `master` before copying
  `buildSrc`, so the pull keeps the value.

### gRPC's own Netty cannot be raised yet

The gRPC port, `8484`, runs on the Netty bundled inside `grpc-netty-shaded`. That
copy is relocated within the JAR, so no Gradle rule reaches it. `grpc-netty-shaded`
1.83.1 bundled 4.2.15.Final. The `config` pull below brings 1.85.0 (2026-10-14),
the newest grpc-java release, which bundles 4.2.17.Final. That is still short of
the 4.2.18.Final fixes, and grpc-java's `master` is at 4.2.17.Final too.
**Do not bump gRPC for Netty's sake until a release bundles 4.2.18.Final or newer.**
Re-check grpc-java releases.

## Other dependency updates (2026-10-14, version 0.23.2)

- `config` pulled from `3409e7dd` to `a0cbbbfe` (config PRs #792 and #795).
  In the image this brings gRPC 1.85.0, Protobuf 4.36.2, Kotlin 2.4.21,
  Guava 33.7.2-jre, Jackson 2.22.3, and SLF4J 2.0.20. It also applies
  `.claude/settings.json` from config PR #793, at the user's request: `git commit`
  no longer asks, while force-pushes and `--mirror` pushes do.
- `Micronaut.version` 4.10.17 → 4.10.21 (2026-10-09), with `coreVersion`
  4.10.26 → 4.10.31. The old KDoc called 4.10.17 the last 4.x release, but the
  4.x line went on. 4.10.21 pins Netty 4.2.19.Final itself, so the launcher's
  pin now equals the platform's, and the `admin-server` module resolves
  4.2.19.Final even without the launcher's override. The platform leaves the
  other aligned pins unchanged: `micronaut-reactor`, Serde, SourceGen, Groovy,
  Reactor Core, and RxJava.
- `Redisson.version` 4.7.0 → 4.8.0 (2026-10-02), a feature release. Among its
  compile-scope dependencies only Kryo changes, 5.6.2 → 5.7.0, which brings
  Objenesis 3.6. It still requests Byte Buddy 1.18.2 and SnakeYAML 2.6, so those
  pins stay.
- `Jetty` 10.0.6 (2021) → 10.0.26 in the `demo-appengine-11` deployment, which
  is not part of the image. Jetty 10 reached its [end of life][jetty-10-eol] in
  January 2025, but Jetty 11 switched to `jakarta.servlet`, which neither the
  demo servlets nor the App Engine runtime uses, so the demo stays on Jetty 10.
- Kept: Reactor Core 3.7.12, the Micronaut Platform's pin, over the 3.8.5 that
  Redisson has requested since 4.5.0. The launcher comment claimed Redisson
  requested an older patch; it now says the platform's pin wins on purpose, and
  the KDoc of `Micronaut.reactorCoreVersion` says why. Micronaut is the image's
  only Reactor user and is built against 3.7.12. Every Redisson source file that
  imports Reactor belongs to its reactive API, which this project does not call.
  Reactor 3.8.0 removes no API, so 3.8.x would also work, but no image test
  drives Micronaut's HTTP stack on it. OSV lists no advisory for either version.
- Kept: `CommonsCompress` 1.26.0 and `CommonsIo` 2.15.1. Both are forced on the
  root buildscript classpath only and are not shipped. Commons Compress 1.26.0
  is what Jib 3.5.4 declares, and that version requests Commons IO 2.15.1.
  Newer Commons Compress pulls Commons Lang 3 and Commons Codec onto that
  classpath, so each of them would need its own forced version.

## Verification (version 0.23.2, after all the updates above)

- `:delivery-server-cloud-run` runtime classpath resolves 16 `io.netty` modules,
  `netty-bom` among them, at 4.2.19.Final. None of the 93 `io.netty` entries of
  the dependency tree resolves to another version. `:admin-server` resolves its
  55 `io.netty` entries at 4.2.19.Final as well.
- A local `jibDockerBuild` image (`v0.23.2`) holds 15 `netty-*-4.2.19.Final.jar`
  files in `/app/libs`, one per module except the BOM, and no 4.2.16 or 4.2.17
  JAR. Beside them sits `grpc-netty-shaded-1.85.0.jar` with its relocated
  4.2.17 copy, as listed in grpc-java's version catalog at `v1.85.0`. The image
  also holds `micronaut-*-4.10.31`, `redisson-4.8.0` and `kryo-5.7.0` JARs.
- `./gradlew build` passes, including the Docker-backed suites, which ran against
  that image: 407 tests, none failed or skipped. Checkstyle 12.3.1 reports no
  violations. Dependency reports regenerated.

[netty-4.2.18]: https://github.com/netty/netty/releases/tag/netty-4.2.18.Final
[netty-4.2.19]: https://github.com/netty/netty/releases/tag/netty-4.2.19.Final
[jetty-10-eol]: https://github.com/jetty/jetty.project/issues/10485
