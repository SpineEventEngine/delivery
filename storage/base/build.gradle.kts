import io.spine.dependency.local.CoreJvm

dependencies {
    api(CoreJvm.server)
}

apply {
    from(rootDir.toPath().resolve("test-artifacts.gradle"))
}
