import io.spine.internal.dependency.Spine

dependencies {
    api(Spine.server)
}

apply {
    from(rootDir.toPath().resolve("test-artifacts.gradle"))
}
