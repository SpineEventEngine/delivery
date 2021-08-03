import io.spine.internal.dependency.Redisson
import io.spine.internal.dependency.Spine
import io.spine.internal.dependency.Testcontainers

dependencies {
    api(Spine.server)
    implementation(Redisson.lib)
    testImplementation(Testcontainers.lib)
    testImplementation(Testcontainers.junitJupiter)
}
