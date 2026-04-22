plugins {
    id("chat.java-library-conventions")
}

dependencies {
    api(project(":modules:core:kernel"))
    implementation(libs.spring.boot.starter.data.jpa)
    testImplementation(project(":modules:core:testing"))
}
