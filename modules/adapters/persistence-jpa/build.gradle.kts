plugins {
    id("chat.java-library-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(project(":modules:features:identity"))

    runtimeOnly(libs.postgresql)

    testImplementation(project(":modules:core:testing"))
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}
