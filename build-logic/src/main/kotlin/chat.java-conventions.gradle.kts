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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    val userDockerSocket = Path.of(System.getProperty("user.home"), ".docker", "run", "docker.sock")
    val dockerDesktopRawSocket = Path.of(
        System.getProperty("user.home"),
        "Library",
        "Containers",
        "com.docker.docker",
        "Data",
        "docker.raw.sock"
    )

    if (System.getenv("DOCKER_HOST").isNullOrBlank()) {
        when {
            Files.exists(userDockerSocket) ->
                environment("DOCKER_HOST", "unix://${userDockerSocket.toAbsolutePath()}")

            Files.exists(dockerDesktopRawSocket) ->
                environment("DOCKER_HOST", "unix://${dockerDesktopRawSocket.toAbsolutePath()}")
        }
    }

    if (System.getenv("DOCKER_API_VERSION").isNullOrBlank()) {
        environment("DOCKER_API_VERSION", "1.41")
    }

    if (Files.exists(dockerDesktopRawSocket) || Files.exists(userDockerSocket)) {
        val preferredDockerSocket = if (Files.exists(userDockerSocket)) userDockerSocket else dockerDesktopRawSocket
        environment(
            "DOCKER_CLIENT_STRATEGY",
            "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy"
        )
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
        systemProperty("docker.host", "unix://${preferredDockerSocket.toAbsolutePath()}")
        systemProperty("dockerconfig.source", "autoIgnoringUserProperties")
        systemProperty(
            "docker.client.strategy",
            "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy"
        )
    }
}
