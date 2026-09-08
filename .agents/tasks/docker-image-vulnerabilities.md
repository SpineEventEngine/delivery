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
  The Micronaut platform 4.10.17 (the last 4.x) pins 4.2.16; the launcher's
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
