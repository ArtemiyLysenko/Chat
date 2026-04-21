plugins {
    id("chat.java-library-conventions")
}

dependencies {
    api(project(":modules:core:kernel"))
    api(project(":modules:features:messaging"))
    implementation(project(":modules:features:rooms"))
    implementation(project(":modules:features:contacts"))
    implementation(libs.spring.boot.starter.data.jpa)

    testImplementation(project(":modules:core:testing"))
}
