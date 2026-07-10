import io.spine.dependency.local.CoreJvm
import io.spine.dependency.test.Testcontainers

dependencies {
    api(CoreJvm.server)
    implementation(project(":storage:base"))
    implementation("com.hazelcast:hazelcast:5.3.1")
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
    testImplementation(project(path = ":storage:base", configuration = "testArtifacts"))
}
