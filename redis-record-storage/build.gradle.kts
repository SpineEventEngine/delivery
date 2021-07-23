import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Testcontainers

dependencies {
    api(Spine.server)
    implementation("org.redisson:redisson:3.16.0")
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
}
