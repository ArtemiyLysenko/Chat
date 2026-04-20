plugins {
    id("chat.java-library-conventions")
}

dependencies {
    implementation(project(":modules:features:messaging"))
    implementation(project(":modules:features:presence"))
    implementation(project(":modules:features:federation"))
}
