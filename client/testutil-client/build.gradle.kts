import io.spine.internal.dependency.Spine

dependencies {
    api(project(":base"))
    api(Spine.Stable.Test.server)
}
