import io.spine.dependency.local.CoreJvm

plugins {
    module
}

dependencies {
    api(CoreJvm.server)
}

apply {
    from(rootDir.toPath().resolve("test-artifacts.gradle"))
}
