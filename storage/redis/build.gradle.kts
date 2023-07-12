import io.spine.internal.dependency.Redisson
import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Testcontainers

dependencies {
    api(Spine.server)
    implementation(project(":storage:base"))
    implementation(Redisson.lib)
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
    testImplementation(project(path = ":storage:base", configuration = "testArtifacts"))
}
