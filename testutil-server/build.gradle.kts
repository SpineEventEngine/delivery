import io.spine.dependency.local.CoreJvm

dependencies {
    api(project(":model"))
    api(CoreJvm.testUtilServer)
}
