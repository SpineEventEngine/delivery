import io.spine.dependency.local.CoreJvm
import io.spine.dependency.storage.Redisson
import io.spine.dependency.test.Kotest
import io.spine.dependency.test.Testcontainers

dependencies {
    api(CoreJvm.server)
    implementation(project(":storage:base"))
    implementation(Redisson.lib)
    testImplementation(Kotest.assertions)
    testImplementation(Testcontainers.lib)
    testImplementation(project(path = ":storage:base", configuration = "testArtifacts"))
}

// The Testcontainers-based suites here (`RedisRecordStorageTest`, `MultitenantStorageTest`,
// `RedisGroupedStorageSpec`) start a `redis:6-alpine` container. Docker is enforced by the
// `checkDockerAvailable` gate wired from the root build, which lists this module in
// `dockerDependentModules()`.
