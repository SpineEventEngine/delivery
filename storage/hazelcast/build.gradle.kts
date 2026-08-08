import io.spine.dependency.local.CoreJvm
import io.spine.dependency.test.Kotest

dependencies {
    api(CoreJvm.server)
    implementation(project(":storage:base"))
    implementation("com.hazelcast:hazelcast:5.3.1")
    testImplementation(Kotest.assertions)
    testImplementation(project(path = ":storage:base", configuration = "testArtifacts"))
}
