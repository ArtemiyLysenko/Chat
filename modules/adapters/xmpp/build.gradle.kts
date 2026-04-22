plugins {
    id("chat.java-library-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(project(":modules:features:identity"))
    implementation(project(":modules:features:contacts"))
    implementation(project(":modules:features:messaging"))
    implementation(project(":modules:features:presence"))
    implementation(project(":modules:features:federation"))
}
