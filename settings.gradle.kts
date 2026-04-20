pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
    }
}

rootProject.name = "chat"

include("apps:api")
include("modules:core:kernel")
include("modules:core:testing")
include("modules:features:identity")
include("modules:features:rooms")
include("modules:features:contacts")
include("modules:features:messaging")
include("modules:features:attachments")
include("modules:features:presence")
include("modules:features:federation")
include("modules:features:admin")
include("modules:adapters:persistence-jpa")
include("modules:adapters:storage-filesystem")
include("modules:adapters:xmpp")
include("tools:load-tests:federation")
