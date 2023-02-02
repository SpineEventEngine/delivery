import io.spine.internal.dependency.Grpc

plugins {
    id("java")
    id("io.micronaut.application") version "3.7.0"
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("io.spine.message.delivery.admin.AdminServer")
}

micronaut {
    version.set("3.5.1")

    runtime("netty")

    processing {
        incremental(true)
        annotations("io.spine.message.delivery.admin.*")
    }
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.security:micronaut-security-annotations")

    implementation("io.micronaut:micronaut-runtime:3.8.1")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.security:micronaut-security")
    implementation(project(":model"))
    implementation(Grpc.nettyShaded)
    implementation("io.grpc:grpc-all:${Grpc.version}")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("io.micronaut.test:micronaut-test-core")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.micronaut.reactor:micronaut-reactor-http-client")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
