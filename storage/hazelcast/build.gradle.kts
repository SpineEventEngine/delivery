import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Testcontainers

dependencies {
    api(Spine.server)
    implementation(project(":storage:base"))
    implementation("com.hazelcast:hazelcast:5.3.1")

    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
}
