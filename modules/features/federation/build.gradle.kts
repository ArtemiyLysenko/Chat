plugins {
    id("chat.java-library-conventions")
}

dependencies {
    api(project(":modules:core:kernel"))
    testImplementation(project(":modules:core:testing"))
}
