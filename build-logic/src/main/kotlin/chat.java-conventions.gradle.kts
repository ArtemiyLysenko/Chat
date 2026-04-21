import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.nio.file.Files
import java.nio.file.Path

plugins {
    java
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
val javaVersion = libs.findVersion("java").get().requiredVersion.toInt()
val userDockerSocket = Path.of(System.getProperty("user.home"), ".docker", "run", "docker.sock")
val dockerDesktopRawSocket = Path.of(
    System.getProperty("user.home"),
    "Library",
    "Containers",
    "com.docker.docker",
    "Data",
    "docker.raw.sock"
)

fun preferredDockerSocket(): Path? =
    when {
        Files.exists(userDockerSocket) -> userDockerSocket
        Files.exists(dockerDesktopRawSocket) -> dockerDesktopRawSocket
        else -> null
    }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    val dockerSocket = preferredDockerSocket()

    if (System.getenv("DOCKER_HOST").isNullOrBlank() && dockerSocket != null) {
        environment("DOCKER_HOST", "unix://${dockerSocket.toAbsolutePath()}")
    }

    if (System.getenv("DOCKER_API_VERSION").isNullOrBlank()) {
        environment("DOCKER_API_VERSION", "1.41")
    }

    // Keep Docker socket selection centralized at the build level so test code stays environment-agnostic.
    if (dockerSocket != null) {
        environment(
            "DOCKER_CLIENT_STRATEGY",
            "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy"
        )
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
        systemProperty("docker.host", "unix://${dockerSocket.toAbsolutePath()}")
        systemProperty("dockerconfig.source", "autoIgnoringUserProperties")
        systemProperty(
            "docker.client.strategy",
            "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy"
        )
    }
}
