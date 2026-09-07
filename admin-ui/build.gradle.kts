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

import com.github.gradle.node.npm.task.NpmInstallTask
import com.github.gradle.node.npm.task.NpxTask

plugins {
  module
  id("com.github.node-gradle.node") version "3.5.1"
}

dependencies {
  implementation(project(":delivery-model"))
  implementation(project(":grpc-api"))
}

node {
  version.set("22.23.2")
  download.set(true)
}

/**
 * A task that runs `npx quasar dev` command.
 */
abstract class Serve : NpxTask() {
  init {
    description = "Equivalent to `npx quasar dev` command."
    command.set("quasar")
    args.set(listOf("dev"))
  }
}

/**
 * A task that runs `npx quasar build` command.
 *
 * The files the build reads are declared as [sources], so that Gradle reruns the task
 * when any of them changes and skips it otherwise. A task with an output directory but
 * no inputs counts as up to date whenever that directory exists, which let a stale
 * bundle go into the server image.
 */
abstract class Build : NpxTask() {
  init {
    description = "Equivalent to `npx quasar build` command."
    command.set("quasar")
    args.set(listOf("build"))
  }

  /** The sources and configuration files `quasar build` reads. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ConfigurableFileCollection

  /** The directory `quasar build` writes the SPA bundle to. */
  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty
}

/**
 * A task that runs `npx quasar clean` command.
 */
abstract class Clean : NpxTask() {
  init {
    description = "Equivalent to `npx quasar clean` command."
    command.set("quasar")
    args.set(listOf("clean"))
  }
}

/**
 * A task that runs the `npx buf generate build/extracted-include-protos/main` command.
 *
 * The `--exclude-path .../src` option drops the redundant copy of the Protobuf
 * well-known types that `protobuf-kotlin` ships under a non-standard
 * `src/google/protobuf/` prefix. The canonical copies (from `protobuf-java`) sit
 * at `google/protobuf/`, so `extractIncludeProto` unions both into the input
 * directory. `buf generate` compiles every `.proto` it finds, and without the
 * exclusion the duplicated well-known types collide ("symbol already defined").
 */
abstract class GenerateTsProto : NpxTask() {
  init {
    description = "Generates TypeScript files from `.proto` definitions."
    command.set("buf")
    args.set(
      listOf(
        "generate",
        "build/extracted-include-protos/main",
        "--exclude-path",
        "build/extracted-include-protos/main/src",
      )
    )
  }
}

val npmInstall = tasks.getByName(NpmInstallTask.NAME)
val generateProto = tasks.withType(GenerateTsProto::class)

/**
 * Runs the `npx quasar dev` command.
 *
 * @see Serve
 */
tasks.register<Serve>("qserve") {
  dependsOn.add(npmInstall)
  dependsOn.add(generateProto)
}

/**
 * Runs the `npx quasar build` command.
 *
 * @see Build
 */
tasks.register<Build>("qbuild") {
  dependsOn.add(npmInstall)
  dependsOn.add(generateProto)
  // `src` includes the TypeScript that `bbgen` generates into `src/gen`.
  sources.from(
    fileTree("src"),
    fileTree("public"),
    "index.html",
    "quasar.config.js",
    "postcss.config.js",
    "tsconfig.json",
    ".eslintrc.js",
    ".eslintignore",
    "package.json",
    "package-lock.json",
  )
  outputDir.set(layout.projectDirectory.dir("dist/spa"))
}

/**
 * Runs the `npx quasar clean` command.
 *
 * @see Clean
 */
tasks.register<Clean>("qclean")

/**
 * Runs the `npx buf generate` command.
 *
 * @see GenerateTsProto
 */
tasks.register<GenerateTsProto>("bbgen") {
  dependsOn.add(npmInstall)
  dependsOn.add(tasks.named("extractIncludeProto"))
}
