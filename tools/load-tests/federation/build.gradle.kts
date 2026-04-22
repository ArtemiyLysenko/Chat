plugins {
    id("chat.java-application-conventions")
}

application {
    mainClass = "edu.artemiy.chat.loadtest.federation.FederationLoadTestApplication"
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}

dependencies {
    implementation(libs.smack.java8)
    implementation(libs.smack.tcp)
    implementation(libs.smack.im)
}
