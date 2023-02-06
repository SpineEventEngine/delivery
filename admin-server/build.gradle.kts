import io.spine.internal.dependency.Grpc
import io.spine.internal.dependency.Micronaut

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
    version.set(Micronaut.version)

    runtime("netty")

    processing {
        incremental(true)
        annotations("io.spine.message.delivery.admin.*")
    }
}

dependencies {
    annotationProcessor(Micronaut.AnnotationProcessor.httpValidation)
    annotationProcessor(Micronaut.AnnotationProcessor.security)

    implementation(Micronaut.runtime)
    implementation(Micronaut.reactor)
    implementation(Micronaut.security)
    implementation("io.grpc:grpc-all:${Grpc.version}")
    implementation(Grpc.nettyShaded)
    implementation(project(":model"))

    testImplementation(Micronaut.Test.core)
    testImplementation(Micronaut.Test.jUnit5)
    testImplementation(Micronaut.httpClient)
}
