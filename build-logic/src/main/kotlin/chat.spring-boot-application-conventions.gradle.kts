import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("chat.java-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    "implementation"(libs.findLibrary("spring-boot-starter-actuator").get())
    "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}
