import io.spine.internal.dependency.Spine

dependencies {
    api(project(":model"))
    api(Spine.Test.server)
}
