import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    application
    id("chat.java-conventions")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}
