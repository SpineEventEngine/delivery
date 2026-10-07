import io.spine.dependency.local.CoreJvm
import io.spine.dependency.storage.Hazelcast
import io.spine.dependency.test.Kotest

plugins {
    module
}

dependencies {
    api(CoreJvm.server)
    implementation(project(":storage:delivery-storage-base"))
    implementation(Hazelcast.lib)
    testImplementation(Kotest.assertions)
    testImplementation(
        project(path = ":storage:delivery-storage-base", configuration = "testArtifacts")
    )
}
