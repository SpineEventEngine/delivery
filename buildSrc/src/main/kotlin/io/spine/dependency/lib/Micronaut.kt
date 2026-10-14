/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.spine.dependency.lib

/**
 * The Micronaut framework, as used by the `admin-server` module.
 *
 * The [version] is the Micronaut Platform version. The [Gradle plugin][GradlePlugin]
 * turns it into the `micronaut-platform` BOM
 * (`micronaut { version.set(Micronaut.version) }`), so the module artifacts below
 * are declared without versions.
 *
 * @see <a href="https://micronaut.io/">Micronaut official website</a>
 */
@Suppress("unused", "ConstPropertyName")
object Micronaut {

    /**
     * The Micronaut Gradle plugin, applied via the `micronaut-application`
     * accessor declared in `DsBuildExtensions.kt`.
     *
     * The plugin versions independently of the framework: 4.6.2 is the newest
     * version that runs on a Java 17 daemon — the 5.x line (the first to
     * officially support Gradle 9) is compiled for JVM 25. In practice, 4.6.2
     * configures and runs cleanly on Gradle 9.6.1.
     *
     * @see <a href="https://plugins.gradle.org/plugin/io.micronaut.application">Micronaut Gradle plugin</a>
     */
    object GradlePlugin {
        const val id = "io.micronaut.application"
        const val version = "4.6.2"
    }

    /** The group of the Micronaut core modules. */
    const val group = "io.micronaut"

    /**
     * The version of the Micronaut Platform, taken from the 4.x line, the highest line
     * this project can run.
     *
     * The 4.x line still receives patch releases; check for a newer one when updating
     * this version. The 5.x line is compiled for JVM 25 — `micronaut-core:5.1.10` is
     * a class file of major version 69 — while this project targets Java 17
     * (`BuildSettings.javaVersion`). Moving to 5.x therefore requires raising the
     * project's toolchain first, together with [the Gradle plugin][GradlePlugin],
     * which is compiled for JVM 25 as well.
     *
     * @see <a href="https://repo1.maven.org/maven2/io/micronaut/platform/micronaut-platform/">Micronaut Platform releases at Maven Central</a>
     */
    const val version = "4.10.21"
    const val bom = "$group.platform:micronaut-platform:$version"

    /**
     * The versions pinned by the platform [bom], repeated here because this build
     * runs with `failOnVersionConflict()`.
     *
     * The Micronaut module POMs request slightly older patch versions of each other
     * than the ones the platform pins, which registers as a version conflict.
     * The `alignMicronautPlatform()` extension (see `MicronautAlignment.kt`) aligns
     * each group to the platform's pick. Update together with [version].
     */
    const val coreVersion = "4.10.31"
    const val reactorVersion = "3.9.1"
    const val serdeVersion = "2.16.2"
    const val sourcegenVersion = "1.8.5"
    const val groovyVersion = "4.0.28"

    /**
     * Versions of the third-party libraries pinned by the platform [bom] that win
     * the cross-stack conflicts on the `delivery-server-cloud-run` classpath, where
     * the Micronaut graph meets the Redisson one. Update together with [version].
     *
     * [nettyVersion] settles the Netty conflicts. It equals the platform's pin,
     * `4.2.19.Final`. That release and `4.2.18.Final` before it are security releases;
     * their fixes include those for HTTP/1 request smuggling and resource exhaustion
     * in `netty-codec-http`, the codec behind the Admin server's HTTP port.
     * The launcher forces [nettyVersion] onto every `io.netty` artifact of
     * the published image except `netty-tcnative-*`, including those Redisson
     * requests. The Netty copy relocated inside `grpc-netty-shaded` is beyond
     * the reach of this pin. When the platform lags behind a Netty security release,
     * raise [nettyVersion] above the platform's pin until the platform catches up.
     *
     * The constant is kept equal to the version of the [Netty] catalog object, so
     * that a module depending on Netty directly resolves what the launcher forces.
     * The two remain separate constants because a future platform may pin a Netty
     * other than the catalog's.
     *
     * [reactorCoreVersion] is the exception to "the newest requested version wins":
     * the launcher takes it although Redisson requests a newer Reactor Core (`3.8.5`
     * as of Redisson 4.8.0). Micronaut, the only Reactor user in the image, is built
     * against the platform's pin. Redisson uses Reactor only in its reactive API
     * (`RedissonReactiveClient` and the `org.redisson.reactive` package), and this
     * project calls only `RedissonClient`, so none of that code is loaded. Revisit
     * when code starts using Redisson's reactive API, or when the platform moves to
     * Reactor Core 3.8.
     */
    const val nettyVersion = "4.2.19.Final"
    const val reactorCoreVersion = "3.7.12"
    const val rxJavaVersion = "3.1.12"

    const val runtime = "$group:micronaut-runtime"

    /**
     * The `JsonMapper` implementation.
     *
     * Micronaut 3 shipped Jackson with the runtime; since Micronaut 4
     * an implementation must be chosen explicitly.
     */
    const val jacksonDatabind = "$group:micronaut-jackson-databind"

    /**
     * The YAML support for the configuration files.
     *
     * Optional since Micronaut 4; required by applications configured
     * by `application.yml`. The version comes from the platform [bom].
     */
    const val snakeYaml = "org.yaml:snakeyaml"

    const val reactor = "$group.reactor:micronaut-reactor"
    const val security = "$group.security:micronaut-security"
    const val httpClient = "$group.reactor:micronaut-reactor-http-client"

    object AnnotationProcessor {
        const val httpValidation = "$group:micronaut-http-validation"
        const val security = "$group.security:micronaut-security-annotations"
    }

    object Test {
        const val core = "$group.test:micronaut-test-core"
        const val jUnit5 = "$group.test:micronaut-test-junit5"
    }
}
