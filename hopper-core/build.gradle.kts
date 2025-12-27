plugins {
    id("java")
    id("java-library")
    id("com.gradleup.shadow")
}

dependencies {
    // No external dependencies - only JDK built-ins
    compileOnly("org.jetbrains:annotations:24.0.1")
}

tasks.jar {
    archiveClassifier.set("")
}

tasks.shadowJar {
    archiveClassifier.set("all")
    archiveBaseName.set("hopper-core")
}
