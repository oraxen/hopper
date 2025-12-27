plugins {
    id("java")
    id("java-library")
    id("com.gradleup.shadow")
}

dependencies {
    api(project(":hopper-core"))
    
    // Paper API (compileOnly - provided by server)
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveClassifier.set("")
}

tasks.shadowJar {
    archiveClassifier.set("all")
    archiveBaseName.set("hopper-paper")
}
