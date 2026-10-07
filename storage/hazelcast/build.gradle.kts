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

tasks.test {
    // The shipped `hazelcast.yaml` makes a member join any `delivery` cluster it reaches
    // by multicast. On a Linux CI host this includes the Delivery server containers that
    // `client/integration-test` starts on the Docker bridge network, so the members started
    // by these tests would join them and exchange records with them. Keep these members
    // standalone. `Config.load()` applies the `hz.*` system properties over the shipped file;
    // `HazelcastConfigSpec` parses the file directly, so it still checks the shipped values.
    systemProperty("hz.clustername", "delivery-storage-test")
    systemProperty("hz.network.join.multicast.enabled", "false")
}
