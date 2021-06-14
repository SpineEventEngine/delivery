import io.spine.internal.dependency.CheckStyle
import io.spine.internal.dependency.Pmd

plugins {
    java
    pmd
    checkstyle
}

pmd {
    toolVersion = Pmd.version
    isConsoleOutput = true
    // The build is going to fail in case of violations.
    isIgnoreFailures = false
    incrementalAnalysis.set(true)

    ruleSets = listOf()
    ruleSetFiles = files("${rootDir}/buildSrc/src/main/resources/pmd.xml")

    reportsDir = file("build/reports/pmd")
    sourceSets = listOf(project.sourceSets.named("main").get())
}

checkstyle {
    toolVersion = CheckStyle.version

    // The build is going to fail in case of violations.
    isIgnoreFailures = false

    configFile = file("${rootDir}/buildSrc/src/main/resources/checkstyle.xml")

    reportsDir = file("build/reports/checkstyle")
    sourceSets = listOf(project.sourceSets.named("main").get())
}
