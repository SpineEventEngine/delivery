import com.github.gradle.node.npm.task.NpmInstallTask
import com.github.gradle.node.npm.task.NpxTask

plugins {
  id("com.github.node-gradle.node") version "3.5.1"
}

node {
  version.set("19.6.0")
  download.set(true)
}

/**
 * Task that runs `npx quasar dev` command.
 */
abstract class Serve : NpxTask() {
  init {
    description = "Equivalent to `npx quasar dev` command."
    command.set("quasar")
    args.set(listOf("dev"))
  }
}

/**
 * Task that runs `npx quasar build` command.
 */
abstract class Build : NpxTask() {
  init {
    description = "Equivalent to `npx quasar build` command."
    command.set("quasar")
    args.set(listOf("build"))
  }

  @OutputDirectory
  fun getOutputDir(): File = File(project.projectDir, "dist/spa")
}

/**
 * Task that runs `npx quasar clean` command.
 */
abstract class Clean : NpxTask() {
  init {
    description = "Equivalent to `npx quasar clean` command."
    command.set("quasar")
    args.set(listOf("clean"))
  }
}

val npmInstall = tasks.getByName(NpmInstallTask.NAME)

/**
 * Task names start with "q" that stands for "quasar".
 */
tasks.register<Serve>("qserve") {
  dependsOn.add(npmInstall)
}
tasks.register<Build>("qbuild") {
  dependsOn.add(npmInstall)
}
tasks.register<Clean>("qclean")
