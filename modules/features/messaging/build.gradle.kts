plugins {
    id("chat.java-library-conventions")
}

dependencies {
    api(project(":modules:core:kernel"))
    implementation(project(":modules:features:rooms"))
    implementation(project(":modules:features:contacts"))
    implementation(libs.spring.boot.starter.data.jpa)
    testImplementation(project(":modules:core:testing"))
}
