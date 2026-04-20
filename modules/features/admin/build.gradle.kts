plugins {
    id("chat.java-library-conventions")
}

dependencies {
    api(project(":modules:core:kernel"))
    implementation(project(":modules:features:federation"))
}
