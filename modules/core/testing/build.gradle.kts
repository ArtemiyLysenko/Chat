plugins {
    id("chat.java-library-conventions")
}

dependencies {
    api(project(":modules:core:kernel"))
    implementation(libs.spring.boot.starter.test)
    implementation(libs.testcontainers.junit.jupiter)
    implementation(libs.testcontainers.postgresql)
}
